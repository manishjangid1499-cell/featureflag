package com.featureflag.notification_service.validation;

import com.featureflag.notification_service.exception.UnsupportedNotificationChannelException;

public final class NotificationTypePolicy {

    public static final String EMAIL = "EMAIL";

    private NotificationTypePolicy() {
    }

    public static String requireExplicitEmail(String type) {
        if (!EMAIL.equals(type)) {
            throw new UnsupportedNotificationChannelException();
        }

        return EMAIL;
    }

    public static String resolveKafkaType(String type) {
        return type == null
                ? EMAIL
                : requireExplicitEmail(type);
    }

    public static String resolveInternalType(String type) {
        return type == null
                ? EMAIL
                : requireExplicitEmail(type);
    }

    public static boolean isEmail(String type) {
        return EMAIL.equals(type);
    }
}
