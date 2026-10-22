package com.huddle.backend.service;

import com.huddle.backend.model.ProjectPost;
import com.huddle.backend.model.User;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MatchingService {

    private static final double SKILL_WEIGHT = 0.7;
    private static final double AVAILABILITY_WEIGHT = 0.3;

    public record MatchResult(ProjectPost post, double score, Set<String> matchedSkills) {}
    public record UserMatchResult(User candidate, double score, Set<String> matchedSkills) {}

    public List<MatchResult> rankPostsForCandidate(User candidate, List<ProjectPost> openPosts) {
        return openPosts.stream()
                .filter(post -> !post.getPoster().getId().equals(candidate.getId()))
                .map(post -> score(candidate, post))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingDouble(MatchResult::score).reversed())
                .collect(Collectors.toList());
    }

    public List<UserMatchResult> rankCandidatesForPost(ProjectPost post, List<User> candidates) {
        return candidates.stream()
                .filter(candidate -> !candidate.getId().equals(post.getPoster().getId()))
                .map(candidate -> scoreCandidate(candidate, post))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingDouble(UserMatchResult::score).reversed())
                .collect(Collectors.toList());
    }

    private MatchResult score(User candidate, ProjectPost post) {
        Set<String> desired = normalize(post.getDesiredSkills());
        Set<String> has = normalize(candidate.getSkillNames()); // getSkillNames() extracts name from Set<Skill>

        Set<String> matched = new HashSet<>(desired);
        matched.retainAll(has);

        double skillScore = desired.isEmpty() ? 0.0 : (double) matched.size() / desired.size();

        double availabilityScore = hasAnyOverlap(candidate.getAvailability(), post.getDesiredAvailability()) ? 1.0 : 0.0;

        double composite = (skillScore * SKILL_WEIGHT) + (availabilityScore * AVAILABILITY_WEIGHT);

        // If skillScore is 0, we should probably exclude it entirely as per requirements ("0% skill = excluded"), 
        // but the requirements state "Keep the existing filter that excludes score <= 0". Wait, the prompt says "0% skill = excluded".
        // Let's force score to 0 if skillScore is 0 so it gets filtered out.
        if (skillScore == 0.0) {
            composite = 0.0;
        }

        return new MatchResult(post, composite, matched);
    }

    private UserMatchResult scoreCandidate(User candidate, ProjectPost post) {
        Set<String> desired = normalize(post.getDesiredSkills());
        Set<String> has = normalize(candidate.getSkillNames()); // getSkillNames() extracts name from Set<Skill>

        Set<String> matched = new HashSet<>(desired);
        matched.retainAll(has);

        double skillScore = desired.isEmpty() ? 0.0 : (double) matched.size() / desired.size();

        double availabilityScore = hasAnyOverlap(candidate.getAvailability(), post.getDesiredAvailability()) ? 1.0 : 0.0;

        double composite = (skillScore * SKILL_WEIGHT) + (availabilityScore * AVAILABILITY_WEIGHT);

        if (skillScore == 0.0) {
            composite = 0.0;
        }

        return new UserMatchResult(candidate, composite, matched);
    }

    private boolean hasAnyOverlap(Set<String> a, Set<String> b) {
        Set<String> setA = normalize(a);
        Set<String> setB = normalize(b);
        if (setA.isEmpty() || setB.isEmpty()) return false;

        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        return !intersection.isEmpty();
    }

    private Set<String> normalize(Set<String> input) {
        if (input == null) return Collections.emptySet();
        return input.stream().map(String::trim).map(String::toLowerCase).collect(Collectors.toSet());
    }
}
