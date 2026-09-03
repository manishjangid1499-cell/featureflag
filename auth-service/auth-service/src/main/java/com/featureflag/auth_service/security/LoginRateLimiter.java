package com.featureflag.auth_service.security;

import com.featureflag.auth_service.config.LoginRateLimitProperties;
import com.featureflag.auth_service.observability.AuthMetrics;
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
public class LoginRateLimiter {

    private final LoginAttemptStore attemptStore;
    private final LoginRateLimitProperties properties;
    private final AuthMetrics authMetrics;

    public LoginRateLimiter(
            LoginAttemptStore attemptStore,
            LoginRateLimitProperties properties,
            AuthMetrics authMetrics
    ) {
        this.attemptStore = attemptStore;
        this.properties = properties;
        this.authMetrics = authMetrics;
    }

    public void checkAllowed(String normalizedEmail) {
        if (!properties.isEnabled()) {
            return;
        }

        Optional<Duration> retryAfter;
        try {
            retryAfter = attemptStore.retryAfter(accountKey(normalizedEmail));
        } catch (RuntimeException exception) {
            backendFailure("check", exception);
            return;
        }

        if (retryAfter.isPresent()) {
            authMetrics.loginRateLimited();
            long seconds = Math.max(
                    1,
                    (retryAfter.get().toMillis() + 999) / 1_000
            );
            throw new LoginRateLimitExceededException(seconds);
        }
    }

    public void recordFailure(String normalizedEmail) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            attemptStore.recordFailure(accountKey(normalizedEmail));
        } catch (RuntimeException exception) {
            backendFailure("record", exception);
        }
    }

    public void recordSuccess(String normalizedEmail) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            attemptStore.reset(accountKey(normalizedEmail));
        } catch (RuntimeException exception) {
            backendFailure("reset", exception);
        }
    }

    private String accountKey(String normalizedEmail) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizedEmail.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void backendFailure(String operation, RuntimeException exception) {
        authMetrics.loginRateLimitBackendFailure(operation);
        log.error(
                "Login rate limiter failed open; operation={} errorType={}",
                operation,
                exception.getClass().getSimpleName()
        );
    }
}
