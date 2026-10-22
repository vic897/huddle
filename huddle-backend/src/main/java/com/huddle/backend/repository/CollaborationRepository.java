package com.huddle.backend.repository;

import com.huddle.backend.model.Collaboration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CollaborationRepository extends JpaRepository<Collaboration, String> {

    Optional<Collaboration> findByProjectPostId(String projectPostId);

    @Query("SELECT c FROM Collaboration c JOIN c.members m WHERE m.user.id = :userId AND m.status = 'ACTIVE'")
    List<Collaboration> findActiveByUserId(@Param("userId") String userId);
}