package com.featureflag.auth_service.security;

import com.featureflag.auth_service.config.LoginRateLimitProperties;
import com.featureflag.auth_service.observability.AuthMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginRateLimiterTest {

    private LoginRateLimitProperties properties;
    private MutableClock clock;
    private InMemoryLoginAttemptStore store;
    private SimpleMeterRegistry registry;
    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        properties = new LoginRateLimitProperties();
        properties.setMaxFailures(2);
        properties.setWindow(Duration.ofMinutes(5));
        properties.setMaxEntries(100);
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        store = new InMemoryLoginAttemptStore(properties, clock);
        registry = new SimpleMeterRegistry();
        limiter = new LoginRateLimiter(
                store,
                properties,
                new AuthMetrics(registry)
        );
    }

    @Test
    void repeatedFailuresBlockOnlyTheAffectedAccountAndExposeMetric() {
        limiter.recordFailure("first@example.com");
        limiter.recordFailure("first@example.com");

        assertThatThrownBy(() -> limiter.checkAllowed("first@example.com"))
                .isInstanceOf(LoginRateLimitExceededException.class)
                .extracting("retryAfterSeconds")
                .isEqualTo(300L);
        assertThatCode(() -> limiter.checkAllowed("second@example.com"))
                .doesNotThrowAnyException();
        assertThat(registry.counter(
                "feature.flag.auth.login.rate.limit.blocks"
        ).count()).isEqualTo(1.0);
    }

    @Test
    void successfulLoginResetAndWindowExpiryBothRestoreAccess() {
        limiter.recordFailure("user@example.com");
        limiter.recordFailure("user@example.com");
        limiter.recordSuccess("user@example.com");
        assertThatCode(() -> limiter.checkAllowed("user@example.com"))
                .doesNotThrowAnyException();

        limiter.recordFailure("user@example.com");
        limiter.recordFailure("user@example.com");
        clock.advance(Duration.ofMinutes(5));
        assertThatCode(() -> limiter.checkAllowed("user@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void backendFailureFailsOpenAndUsesOnlyHashedAccountKey() {
        LoginAttemptStore failingStore = mock(LoginAttemptStore.class);
        when(failingStore.retryAfter(argThat(key ->
                key.length() == 64 && !key.contains("@")
        ))).thenThrow(new IllegalStateException("store down"));
        LoginRateLimiter failingLimiter = new LoginRateLimiter(
                failingStore,
                properties,
                new AuthMetrics(registry)
        );

        assertThatCode(() ->
                failingLimiter.checkAllowed("user@example.com")
        ).doesNotThrowAnyException();

        verify(failingStore).retryAfter(argThat(key ->
                key.length() == 64 && !key.contains("user@example.com")
        ));
        assertThat(registry.counter(
                "feature.flag.auth.login.rate.limit.backend.failures",
                "operation", "check"
        ).count()).isEqualTo(1.0);
    }

    @Test
    void accountTrackingRemainsBoundedUnderConcurrentFailures() {
        IntStream.range(0, 500)
                .parallel()
                .forEach(index -> limiter.recordFailure(
                        "user-" + index + "@example.com"
                ));

        assertThat(store.trackedAccountCount()).isEqualTo(100);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
