package com.featureflag.notification_service.service;

public record DeliveryClaim(
        Long notificationId,
        String claimToken,
        String recipient,
        String subject,
        String message,
        int attemptCount
) {

    @Override
    public String toString() {
        return "DeliveryClaim[notificationId=" + notificationId
                + ", attemptCount=" + attemptCount + "]";
    }
}
