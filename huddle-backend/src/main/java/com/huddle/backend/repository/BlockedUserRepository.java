package com.huddle.backend.repository;

import com.huddle.backend.model.BlockedUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BlockedUserRepository extends JpaRepository<BlockedUser, String> {
    List<BlockedUser> findByBlockerId(String blockerId);
    List<BlockedUser> findByBlockedId(String blockedId);
    Optional<BlockedUser> findByBlockerIdAndBlockedId(String blockerId, String blockedId);
    boolean existsByBlockerIdAndBlockedId(String blockerId, String blockedId);
}