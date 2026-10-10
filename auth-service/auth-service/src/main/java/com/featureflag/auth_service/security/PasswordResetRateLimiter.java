package com.featureflag.auth_service.security;

import com.featureflag.auth_service.config.LoginRateLimitProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

@Component
@Slf4j
public class PasswordResetRateLimiter {

    private final LoginAttemptStore attemptStore;
    private final LoginRateLimitProperties properties;

    public PasswordResetRateLimiter(
            LoginAttemptStore attemptStore,
            LoginRateLimitProperties properties
    ) {
        this.attemptStore = attemptStore;
        this.properties = properties;
    }

    public boolean isAllowed(String normalizedEmail) {
        if (!properties.isEnabled()) {
            return true;
        }

        try {
            Optional<Duration> retryAfter = attemptStore.retryAfter(accountKey(normalizedEmail));
            return retryAfter.isEmpty();
        } catch (RuntimeException exception) {
            log.error("Password reset rate limiter failed open; errorType={}", exception.getClass().getSimpleName());
            return true;
        }
    }

    public void recordRequest(String normalizedEmail) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            attemptStore.recordFailure(accountKey(normalizedEmail));
        } catch (RuntimeException exception) {
            log.error("Password reset rate limiter failed to record request; errorType={}", exception.getClass().getSimpleName());
        }
    }

    private String accountKey(String normalizedEmail) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("pwd_reset:" + normalizedEmail).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
