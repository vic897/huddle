package com.huddle.backend.service;

import com.huddle.backend.model.BlockedUser;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.BlockedUserRepository;
import com.huddle.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BlockService {

    private final BlockedUserRepository blockedUserRepository;
    private final UserRepository userRepository;

    public BlockService(BlockedUserRepository blockedUserRepository, UserRepository userRepository) {
        this.blockedUserRepository = blockedUserRepository;
        this.userRepository = userRepository;
    }

    public void block(User blocker, String blockedUserId) {
        if (blocker.getId().equals(blockedUserId)) {
            throw new IllegalArgumentException("Cannot block yourself");
        }
        User target = userRepository.findById(blockedUserId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        if (blockedUserRepository.existsByBlockerIdAndBlockedId(blocker.getId(), blockedUserId)) {
            return; // already blocked, idempotent no-op
        }
        BlockedUser block = new BlockedUser();
        block.setBlocker(blocker);
        block.setBlocked(target);
        blockedUserRepository.save(block);
    }

    public void unblock(User blocker, String blockedUserId) {
        blockedUserRepository.findByBlockerIdAndBlockedId(blocker.getId(), blockedUserId)
                .ifPresent(blockedUserRepository::delete);
    }

    // Set of user IDs this user has blocked — used to filter them OUT of browse/matched results.
    public Set<String> getBlockedIds(String userId) {
        return blockedUserRepository.findByBlockerId(userId).stream()
                .map(b -> b.getBlocked().getId())
                .collect(Collectors.toSet());
    }

    // Set of user IDs who have blocked this user — so they don't see me either.
    public Set<String> getBlockedByIds(String userId) {
        return blockedUserRepository.findByBlockedId(userId).stream()
                .map(b -> b.getBlocker().getId())
                .collect(Collectors.toSet());
    }

    // Combined set of all bidirectional blocked IDs
    public Set<String> getAllBlockedIds(String userId) {
        Set<String> all = new HashSet<>(getBlockedIds(userId));
        all.addAll(getBlockedByIds(userId));
        return all;
    }

    public boolean isBlocked(String blockerId, String blockedId) {
        return blockedUserRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId);
    }
}