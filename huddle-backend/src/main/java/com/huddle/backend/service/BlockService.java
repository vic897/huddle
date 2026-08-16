package com.huddle.backend.service;

import com.huddle.backend.model.BlockedUser;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.BlockedUserRepository;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BlockService {

    private final BlockedUserRepository blockedUserRepository;

    public BlockService(BlockedUserRepository blockedUserRepository) {
        this.blockedUserRepository = blockedUserRepository;
    }

    public void block(User blocker, String blockedUserId) {
        if (blocker.getId().equals(blockedUserId)) {
            throw new IllegalArgumentException("Cannot block yourself");
        }
        if (blockedUserRepository.existsByBlockerIdAndBlockedId(blocker.getId(), blockedUserId)) {
            return; // already blocked, no-op
        }
        BlockedUser block = new BlockedUser();
        block.setBlocker(blocker);
        User blockedRef = new User();
        blockedRef.setId(blockedUserId);
        block.setBlocked(blockedRef);
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

    public boolean isBlocked(String blockerId, String blockedId) {
        return blockedUserRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId);
    }
}