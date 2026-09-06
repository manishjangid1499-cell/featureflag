package com.featureflag.notification_service.service;

import com.featureflag.notification_service.entity.Notification;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class NotificationAccessPolicy {

    public boolean canAccess(
            Notification notification,
            String userEmail,
            String userRole
    ) {
        String role = normalizeRole(userRole);
        if ("OWNER".equals(role)) {
            return true;
        }

        boolean recipient = emailsEqual(notification.getRecipient(), userEmail);
        if ("ADMIN".equals(role)) {
            return recipient || emailsEqual(
                    notification.getCreatorEmail(),
                    userEmail
            );
        }
        return recipient;
    }

    public boolean emailsEqual(String first, String second) {
        return first != null
                && second != null
                && first.trim().equalsIgnoreCase(second.trim());
    }

    public String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public String normalizeNullableEmail(String email) {
        return email == null ? null : email.trim();
    }

    public String normalizeRole(String role) {
        return role == null ? "VIEWER" : role.trim().toUpperCase(Locale.ROOT);
    }
}
