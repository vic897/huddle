package com.huddle.backend.repository;

import com.huddle.backend.model.CollaborationMember;
import com.huddle.backend.model.CollaborationMember.MemberStatus;
import com.huddle.backend.model.DepartureStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CollaborationMemberRepository extends JpaRepository<CollaborationMember, String> {

    Optional<CollaborationMember> findByCollaborationIdAndUserId(String collaborationId, String userId);

    List<CollaborationMember> findByCollaborationId(String collaborationId);

    List<CollaborationMember> findByCollaborationIdAndStatus(String collaborationId, MemberStatus status);

    boolean existsByCollaborationIdAndUserIdAndStatus(String collaborationId, String userId, MemberStatus status);

    long countByUserIdAndStatus(String userId, MemberStatus status);

    List<CollaborationMember> findByUserIdAndStatus(String userId, MemberStatus status);

    List<CollaborationMember> findByUserId(String userId);

    @Query("SELECT cm FROM CollaborationMember cm WHERE cm.departureStatus = 'PENDING_APPROVAL' AND cm.departureRequestedAt < :cutoff")
    List<CollaborationMember> findPendingDeparturesOlderThan(@Param("cutoff") Instant cutoff);

    @Query("SELECT cm FROM CollaborationMember cm WHERE cm.departureStatus = 'ESCALATED_ADMIN'")
    List<CollaborationMember> findEscalatedDepartures();

    @Query("SELECT COUNT(cm) > 0 FROM CollaborationMember cm WHERE cm.collaboration.id = :collaborationId AND cm.user.id = :userId AND cm.archiveEligible = true")
    boolean existsByCollaborationIdAndUserIdAndArchiveEligibleTrue(@Param("collaborationId") String collaborationId, @Param("userId") String userId);
}
