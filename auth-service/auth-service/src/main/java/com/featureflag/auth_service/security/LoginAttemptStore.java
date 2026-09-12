package com.featureflag.auth_service.security;

import java.time.Duration;
import java.util.Optional;

public interface LoginAttemptStore {

    Optional<Duration> retryAfter(String accountKey);

    void recordFailure(String accountKey);

    void reset(String accountKey);
}
