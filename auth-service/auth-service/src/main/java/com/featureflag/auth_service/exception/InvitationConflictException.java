package com.featureflag.auth_service.exception;

public class InvitationConflictException extends RuntimeException {

    public InvitationConflictException(String message) {
        super(message);
    }
}
