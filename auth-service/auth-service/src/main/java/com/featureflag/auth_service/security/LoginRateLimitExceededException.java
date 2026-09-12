package com.featureflag.auth_service.security;

public class LoginRateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public LoginRateLimitExceededException(long retryAfterSeconds) {
        super("Too many login attempts");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
