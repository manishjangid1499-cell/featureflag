package com.featureflag.notification_service.repository;

import com.featureflag.notification_service.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByOrderByCreatedAtDesc();

    List<Notification> findByRecipient(String recipient);

    List<Notification> findByRecipientOrderByCreatedAtDesc(String recipient);

    List<Notification> findByRecipientOrCreatorEmailOrderByCreatedAtDesc(String recipient, String creatorEmail);

    List<Notification> findByStatus(String status);
}