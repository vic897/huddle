package com.huddle.backend.controller;

import com.huddle.backend.dto.MyProfileResponse;
import com.huddle.backend.dto.ProfileResponse;
import com.huddle.backend.dto.SkillDto;
import com.huddle.backend.dto.UpdateProfileRequest;
import com.huddle.backend.model.*;
import com.huddle.backend.repository.CollaborationMemberRepository;
import com.huddle.backend.repository.ProjectPostRepository;
import com.huddle.backend.repository.UserRepository;
import com.huddle.backend.service.BlockService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final BlockService blockService;
    private final ProjectPostRepository projectPostRepository;
    private final CollaborationMemberRepository collaborationMemberRepository;

    public UserController(UserRepository userRepository,
                          BlockService blockService,
                          ProjectPostRepository projectPostRepository,
                          CollaborationMemberRepository collaborationMemberRepository) {
        this.userRepository = userRepository;
        this.blockService = blockService;
        this.projectPostRepository = projectPostRepository;
        this.collaborationMemberRepository = collaborationMemberRepository;
    }

    /**
     * GET /api/users?q=...&skills=react,java&availability=weekday-evenings,weekends
     * Authenticated. Returns all users except:
     *   - the caller themselves
     *   - users the caller has blocked
     *   - users who have blocked the caller
     * Optional: q (keyword), skills (comma-sep, ANY match), availability (comma-sep, ANY match).
     */
    @GetMapping
    public ResponseEntity<?> getAllUsers(@AuthenticationPrincipal User currentUser,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) String skills,
                                         @RequestParam(required = false) String availability) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        Set<String> blockedByMe = blockService.getBlockedIds(currentUser.getId());
        Set<String> blockedMe   = blockService.getBlockedByIds(currentUser.getId());

        // Normalise keyword
        final String query = (q != null && !q.isBlank()) ? q.trim().toLowerCase() : null;

        // Normalise skill filter list — lowercase, drop blanks
        final Set<String> skillFilter = (skills != null && !skills.isBlank())
                ? Arrays.stream(skills.split(","))
                        .map(String::trim)
                        .map(String::toLowerCase)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet())
                : Set.of();

        // Normalise availability filter list — lowercase, drop blanks
        final Set<String> availFilter = (availability != null && !availability.isBlank())
                ? Arrays.stream(availability.split(","))
                        .map(String::trim)
                        .map(String::toLowerCase)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet())
                : Set.of();

        List<ProfileResponse> users = userRepository.findAll().stream()
                // Exclude self
                .filter(u -> !u.getId().equals(currentUser.getId()))
                // Exclude bidirectional blocks
                .filter(u -> !blockedByMe.contains(u.getId()))
                .filter(u -> !blockedMe.contains(u.getId()))
                // Optional keyword search: name, bio, or any skill contains query
                .filter(u -> {
                    if (query == null) return true;
                    boolean nameMatch = u.getDisplayName() != null
                            && u.getDisplayName().toLowerCase().contains(query);
                    boolean skillMatch = u.getSkillNames() != null
                            && u.getSkillNames().stream().anyMatch(s -> s.toLowerCase().contains(query));
                    boolean bioMatch = u.getBio() != null
                            && u.getBio().toLowerCase().contains(query);
                    return nameMatch || skillMatch || bioMatch;
                })
                // Skill filter: user must have ANY of the selected skills (case-insensitive)
                .filter(u -> {
                    if (skillFilter.isEmpty()) return true;
                    if (u.getSkillNames() == null || u.getSkillNames().isEmpty()) return false;
                    Set<String> userSkillsLower = u.getSkillNames().stream()
                            .map(s -> s.trim().toLowerCase())
                            .collect(Collectors.toSet());
                    return skillFilter.stream().anyMatch(userSkillsLower::contains);
                })
                // Availability filter: user must have ANY of the selected slots (case-insensitive)
                .filter(u -> {
                    if (availFilter.isEmpty()) return true;
                    if (u.getAvailability() == null || u.getAvailability().isEmpty()) return false;
                    Set<String> userAvailLower = u.getAvailability().stream()
                            .map(s -> s.trim().toLowerCase())
                            .collect(Collectors.toSet());
                    return availFilter.stream().anyMatch(userAvailLower::contains);
                })
                .map(this::buildProfileResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(users);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        // Fetch freshly from repository to guarantee eager associations and updated state
        User user = userRepository.findById(currentUser.getId()).orElse(currentUser);
        int projectsCount = (int) projectPostRepository.countByPosterId(user.getId());
        int collabsCount = (int) collaborationMemberRepository.countByUserIdAndStatus(
                user.getId(), CollaborationMember.MemberStatus.ACTIVE);
        return ResponseEntity.ok(new MyProfileResponse(user, projectsCount, collabsCount));
    }

    /**
     * GET /api/users/{id}
     * Public/Authenticated endpoint for viewing any user's profile.
     * Never exposes email, passwordHash, or roles.
     * Honours block relationships when caller is authenticated.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal User currentUser,
                                        @PathVariable String id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
        }
        User target = userOpt.get();

        // If the viewer is authenticated, check block relationships
        if (currentUser != null) {
            boolean iBlockedThem = blockService.isBlocked(currentUser.getId(), target.getId());
            boolean theyBlockedMe = blockService.isBlocked(target.getId(), currentUser.getId());
            if (iBlockedThem || theyBlockedMe) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Profile not available"));
            }
        }

        return ResponseEntity.ok(buildProfileResponse(target));
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMyProfile(@AuthenticationPrincipal User currentUser,
                                             @Valid @RequestBody UpdateProfileRequest request) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (currentUser.getInstitutionStatus() != com.huddle.backend.model.InstitutionStatus.VERIFIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Institution verification pending or rejected"));
        }

        User user = userRepository.findById(currentUser.getId()).orElse(currentUser);

        // ── Identity ─────────────────────────────────────────────────────────
        if (request.getDisplayName() != null) user.setDisplayName(request.getDisplayName());
        if (request.getBio() != null) user.setBio(request.getBio());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());
        if (request.getCollege() != null) user.setCollege(request.getCollege());
        if (request.getCourse() != null) user.setCourse(request.getCourse());
        if (request.getGraduationYear() != null) user.setGraduationYear(request.getGraduationYear());
        if (request.getLocation() != null) user.setLocation(request.getLocation());

        // ── Social links ─────────────────────────────────────────────────────
        if (request.getGithubUrl() != null) user.setGithubUrl(request.getGithubUrl());
        if (request.getLinkedinUrl() != null) user.setLinkedinUrl(request.getLinkedinUrl());
        if (request.getPortfolioUrl() != null) user.setPortfolioUrl(request.getPortfolioUrl());
        if (request.getTwitterUrl() != null) user.setTwitterUrl(request.getTwitterUrl());

        // ── Availability & Collaboration ─────────────────────────────────────
        if (request.getAvailability() != null) user.setAvailability(request.getAvailability());
        if (request.getOpenToCollaboration() != null) user.setOpenToCollaboration(request.getOpenToCollaboration());
        if (request.getPreferredCollaboration() != null) user.setPreferredCollaboration(request.getPreferredCollaboration());
        if (request.getLookingFor() != null) user.setLookingFor(request.getLookingFor());

        // ── Skills ───────────────────────────────────────────────────────────
        if (request.getSkills() != null) {
            Set<Skill> newSkills = request.getSkills().stream()
                    .filter(s -> s.getName() != null && !s.getName().isBlank())
                    .map(s -> new Skill(
                            s.getName().trim(),
                            s.getCategory() != null ? s.getCategory() : SkillCategory.OTHER,
                            s.getProficiency() != null ? s.getProficiency() : SkillProficiency.BEGINNER
                    ))
                    .collect(Collectors.toSet());
            user.setSkills(newSkills);
        }

        // ── Education ────────────────────────────────────────────────────────
        if (request.getEducation() != null) {
            List<UserEducation> educationList = request.getEducation().stream()
                    .map(e -> {
                        UserEducation edu = new UserEducation();
                        edu.setInstitution(e.getInstitution());
                        edu.setDegree(e.getDegree());
                        edu.setField(e.getField());
                        edu.setStartYear(e.getStartYear());
                        edu.setEndYear(e.getEndYear());
                        edu.setDescription(e.getDescription());
                        return edu;
                    })
                    .collect(Collectors.toList());
            user.setEducation(educationList);
        }

        // ── Experience ───────────────────────────────────────────────────────
        if (request.getExperience() != null) {
            List<UserExperience> experienceList = request.getExperience().stream()
                    .map(e -> {
                        UserExperience exp = new UserExperience();
                        exp.setTitle(e.getTitle());
                        exp.setOrganization(e.getOrganization());
                        exp.setStartDate(e.getStartDate());
                        exp.setEndDate(e.getEndDate());
                        exp.setDescription(e.getDescription());
                        return exp;
                    })
                    .collect(Collectors.toList());
            user.setExperience(experienceList);
        }

        // ── Achievements ─────────────────────────────────────────────────────
        if (request.getAchievements() != null) {
            List<UserAchievement> achievementList = request.getAchievements().stream()
                    .map(a -> {
                        UserAchievement ach = new UserAchievement();
                        ach.setTitle(a.getTitle());
                        ach.setDescription(a.getDescription());
                        ach.setDate(a.getDate());
                        return ach;
                    })
                    .collect(Collectors.toList());
            user.setAchievements(achievementList);
        }

        User saved = userRepository.save(user);
        int projectsCount = (int) projectPostRepository.countByPosterId(saved.getId());
        int collabsCount = (int) collaborationMemberRepository.countByUserIdAndStatus(
                saved.getId(), CollaborationMember.MemberStatus.ACTIVE);

        return ResponseEntity.ok(new MyProfileResponse(saved, projectsCount, collabsCount));
    }

    private ProfileResponse buildProfileResponse(User user) {
        int projectsCount = (int) projectPostRepository.countByPosterId(user.getId());
        int collabsCount = (int) collaborationMemberRepository.countByUserIdAndStatus(
                user.getId(), CollaborationMember.MemberStatus.ACTIVE);
        return new ProfileResponse(user, projectsCount, collabsCount);
    }
}
