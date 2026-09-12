package com.featureflag.auth_service.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AuthMetrics {

    private final MeterRegistry meterRegistry;

    public AuthMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void loginSucceeded() {
        login("success");
    }

    public void loginFailed() {
        login("failure");
    }

    public void loginRateLimited() {
        meterRegistry.counter(
                "feature.flag.auth.login.rate.limit.blocks"
        ).increment();
    }

    public void loginRateLimitBackendFailure(String operation) {
        String boundedOperation = switch (operation) {
            case "check", "record", "reset" -> operation;
            default -> "other";
        };
        meterRegistry.counter(
                "feature.flag.auth.login.rate.limit.backend.failures",
                "operation", boundedOperation
        ).increment();
    }

    private void login(String outcome) {
        meterRegistry.counter(
                "feature.flag.auth.login",
                "outcome", outcome
        ).increment();
    }
}
