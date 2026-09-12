package com.featureflag.notification_service.exception;

public class InvitationDeliveryException
        extends RuntimeException {

    public InvitationDeliveryException() {
        super("Invitation email delivery failed");
    }
}
