package com.featureflag.auth_service.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "security.login-rate-limit")
@Validated
public class LoginRateLimitProperties {

    private boolean enabled = true;

    @Min(1)
    @Max(100)
    private int maxFailures = 5;

    @NotNull
    private Duration window = Duration.ofMinutes(5);

    @Min(100)
    @Max(1_000_000)
    private int maxEntries = 10_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxFailures() {
        return maxFailures;
    }

    public void setMaxFailures(int maxFailures) {
        this.maxFailures = maxFailures;
    }

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        this.window = window;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    @AssertTrue(message = "login rate-limit window must be positive")
    public boolean isWindowPositive() {
        return window != null && !window.isZero() && !window.isNegative();
    }
}
