package com.huddle.backend.repository;

import com.huddle.backend.model.MembershipAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MembershipAuditEventRepository extends JpaRepository<MembershipAuditEvent, String> {

    List<MembershipAuditEvent> findByCollaborationIdOrderByCreatedAtDesc(String collaborationId);

    List<MembershipAuditEvent> findByCollaborationIdAndUserIdOrderByCreatedAtDesc(String collaborationId, String userId);
}
