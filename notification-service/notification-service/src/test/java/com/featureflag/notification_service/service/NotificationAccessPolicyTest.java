package com.featureflag.notification_service.service;

import com.featureflag.notification_service.entity.Notification;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationAccessPolicyTest {

    private final NotificationAccessPolicy policy =
            new NotificationAccessPolicy();

    @Test
    void ownerCanAccessAnyNotification() {
        assertTrue(policy.canAccess(notification(), "other@example.com", "owner"));
    }

    @Test
    void adminCanAccessCreatedOrReceivedNotification() {
        assertTrue(policy.canAccess(notification(), "creator@example.com", "ADMIN"));
        assertTrue(policy.canAccess(notification(), "recipient@example.com", "ADMIN"));
    }

    @Test
    void viewerCanOnlyAccessReceivedNotification() {
        assertTrue(policy.canAccess(notification(), "RECIPIENT@example.com", "VIEWER"));
        assertFalse(policy.canAccess(notification(), "creator@example.com", "VIEWER"));
    }

    private Notification notification() {
        return Notification.builder()
                .recipient("recipient@example.com")
                .creatorEmail("creator@example.com")
                .build();
    }
}
