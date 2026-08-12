package com.huddle.backend.service;

import com.huddle.backend.model.ProjectPost;
import com.huddle.backend.model.User;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

// Matching algorithm v1: explainable weighted scoring (skill complementarity + availability
// overlap), NOT ML — deliberate choice per the project plan, defensible in a viva and
// upgradable to embeddings-based matching later without changing the surrounding system.
@Service
public class MatchingService {

    // Weights are separated out and named so they're easy to justify/tune, not magic numbers
    // buried in a formula.
    private static final double SKILL_WEIGHT = 0.7;
    private static final double AVAILABILITY_WEIGHT = 0.3;

    public record MatchResult(ProjectPost post, double score, Set<String> matchedSkills) {}

    /**
     * Ranks a candidate's compatibility against a list of open posts.
     * Complementarity, not identity: a candidate's skills are scored against what the post
     * is looking for (desiredSkills), not against the poster's own skills — you want someone
     * who FILLS the gap, not someone who duplicates the poster.
     */
    public List<MatchResult> rankPostsForCandidate(User candidate, List<ProjectPost> openPosts) {
        return openPosts.stream()
                .filter(post -> !post.getPoster().getId().equals(candidate.getId())) // don't match yourself
                .map(post -> score(candidate, post))
                .sorted(Comparator.comparingDouble(MatchResult::score).reversed())
                .collect(Collectors.toList());
    }

    private MatchResult score(User candidate, ProjectPost post) {
        Set<String> desired = normalize(post.getDesiredSkills());
        Set<String> has = normalize(candidate.getSkills());

        Set<String> matched = new HashSet<>(desired);
        matched.retainAll(has);

        double skillScore = desired.isEmpty() ? 0.0 : (double) matched.size() / desired.size();

        double availabilityScore = overlapRatio(candidate.getAvailability(), post.getPoster().getAvailability());

        double composite = (skillScore * SKILL_WEIGHT) + (availabilityScore * AVAILABILITY_WEIGHT);

        return new MatchResult(post, composite, matched);
    }

    private double overlapRatio(Set<String> a, Set<String> b) {
        Set<String> setA = normalize(a);
        Set<String> setB = normalize(b);
        if (setA.isEmpty() || setB.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private Set<String> normalize(Set<String> input) {
        if (input == null) return Collections.emptySet();
        return input.stream().map(String::trim).map(String::toLowerCase).collect(Collectors.toSet());
    }
}
