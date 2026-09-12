package com.featureflag.notification_service.dto;

import com.featureflag.notification_service.entity.Notification;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        String recipient,
        String creatorEmail,
        String subject,
        String message,
        String type,
        String status,
        Instant createdAt,
        Instant sentAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getRecipient(),
                notification.getCreatorEmail(),
                notification.getSubject(),
                notification.getMessage(),
                notification.getType(),
                notification.getStatus(),
                notification.getCreatedAt(),
                notification.getSentAt()
        );
    }
}
