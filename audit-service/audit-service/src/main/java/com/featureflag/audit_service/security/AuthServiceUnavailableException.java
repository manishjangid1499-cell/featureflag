package com.featureflag.audit_service.security;

import org.springframework.security.core.AuthenticationException;

public class AuthServiceUnavailableException extends AuthenticationException {

    public AuthServiceUnavailableException(String message) {
        super(message);
    }

    public AuthServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
