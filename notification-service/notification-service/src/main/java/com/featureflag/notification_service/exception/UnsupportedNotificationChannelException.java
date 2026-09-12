package com.featureflag.notification_service.exception;

public class UnsupportedNotificationChannelException
        extends IllegalArgumentException {

    public UnsupportedNotificationChannelException() {
        super("Only EMAIL notification channel is supported");
    }
}
