package com.featureflag.auth_service.security;

import com.featureflag.auth_service.config.LoginRateLimitProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryLoginAttemptStore implements LoginAttemptStore {

    private final Map<String, FailureWindow> attempts =
            new ConcurrentHashMap<>();
    private final LoginRateLimitProperties properties;
    private final Clock clock;

    public InMemoryLoginAttemptStore(
            LoginRateLimitProperties properties,
            Clock clock
    ) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Optional<Duration> retryAfter(String accountKey) {
        Instant now = clock.instant();
        FailureWindow current = attempts.get(accountKey);
        if (current == null) {
            return Optional.empty();
        }
        if (expired(current, now)) {
            attempts.remove(accountKey, current);
            return Optional.empty();
        }
        if (current.failures() < properties.getMaxFailures()) {
            return Optional.empty();
        }
        Duration remaining = Duration.between(
                now,
                current.startedAt().plus(properties.getWindow())
        );
        return remaining.isNegative() || remaining.isZero()
                ? Optional.empty()
                : Optional.of(remaining);
    }

    @Override
    public void recordFailure(String accountKey) {
        Instant now = clock.instant();
        synchronized (attempts) {
            ensureCapacity(accountKey, now);
            attempts.compute(accountKey, (ignored, current) -> {
                if (current == null || expired(current, now)) {
                    return new FailureWindow(1, now);
                }
                return new FailureWindow(
                        Math.min(
                                current.failures() + 1,
                                Integer.MAX_VALUE
                        ),
                        current.startedAt()
                );
            });
        }
    }

    @Override
    public void reset(String accountKey) {
        attempts.remove(accountKey);
    }

    int trackedAccountCount() {
        return attempts.size();
    }

    private boolean expired(FailureWindow window, Instant now) {
        return !now.isBefore(
                window.startedAt().plus(properties.getWindow())
        );
    }

    private void ensureCapacity(String accountKey, Instant now) {
        if (attempts.containsKey(accountKey)) {
            return;
        }

        attempts.entrySet().removeIf(
                entry -> expired(entry.getValue(), now)
        );
        if (attempts.size() >= properties.getMaxEntries()) {
            attempts.entrySet().stream()
                    .min(Comparator.comparing(
                            entry -> entry.getValue().startedAt()
                    ))
                    .map(Map.Entry::getKey)
                    .ifPresent(attempts::remove);
        }
    }

    private record FailureWindow(int failures, Instant startedAt) {
    }
}
