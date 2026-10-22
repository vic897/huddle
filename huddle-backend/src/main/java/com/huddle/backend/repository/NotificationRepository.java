package com.huddle.backend.repository;

import com.huddle.backend.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId);

    long countByRecipientIdAndIsReadFalse(String recipientId);

    // Used to prevent duplicate notifications for the same event
    Optional<Notification> findByRecipientIdAndTypeAndTargetUrl(String recipientId, String type, String targetUrl);
}