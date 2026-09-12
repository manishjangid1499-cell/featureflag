package com.featureflag.notification_service.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.notification.delivery")
public class NotificationDeliveryProperties {

    private boolean enabled = true;

    @Min(1)
    private int maxAttempts = 5;

    @NotNull
    private Duration initialDelay = Duration.ofSeconds(30);

    @Min(1)
    private int multiplier = 2;

    @NotNull
    private Duration maxDelay = Duration.ofMinutes(15);

    @Min(1)
    private int batchSize = 25;

    @NotNull
    private Duration leaseDuration = Duration.ofMinutes(2);

    @NotNull
    private Duration pollInterval = Duration.ofSeconds(5);

    @AssertTrue(
            message = "delivery durations must be positive and maxDelay must not be shorter than initialDelay"
    )
    public boolean isDurationConfigurationValid() {
        return isPositive(initialDelay)
                && isPositive(maxDelay)
                && isPositive(leaseDuration)
                && isPositive(pollInterval)
                && maxDelay.compareTo(initialDelay) >= 0;
    }

    private boolean isPositive(Duration duration) {
        return duration != null
                && !duration.isZero()
                && !duration.isNegative();
    }
}
