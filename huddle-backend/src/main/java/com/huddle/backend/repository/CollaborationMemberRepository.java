package com.huddle.backend.repository;

import com.huddle.backend.model.CollaborationMember;
import com.huddle.backend.model.CollaborationMember.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CollaborationMemberRepository extends JpaRepository<CollaborationMember, String> {

    Optional<CollaborationMember> findByCollaborationIdAndUserId(String collaborationId, String userId);

    List<CollaborationMember> findByCollaborationId(String collaborationId);

    List<CollaborationMember> findByCollaborationIdAndStatus(String collaborationId, MemberStatus status);

    boolean existsByCollaborationIdAndUserIdAndStatus(String collaborationId, String userId, MemberStatus status);

    long countByUserIdAndStatus(String userId, MemberStatus status);
}