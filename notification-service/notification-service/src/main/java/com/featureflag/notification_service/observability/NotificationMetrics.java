package com.featureflag.notification_service.observability;

import com.featureflag.notification_service.repository.NotificationRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NotificationMetrics {

    private final MeterRegistry meterRegistry;
    private final NotificationRepository notificationRepository;

    public NotificationMetrics(
            MeterRegistry meterRegistry,
            NotificationRepository notificationRepository
    ) {
        this.meterRegistry = meterRegistry;
        this.notificationRepository = notificationRepository;

        Gauge.builder(
                        "feature.flag.notification.delivery.dead",
                        this,
                        NotificationMetrics::deadCount
                )
                .description("Terminal notification delivery rows")
                .register(meterRegistry);
    }

    public void eventIngested() {
        meterRegistry.counter(
                "feature.flag.notification.ingestion",
                "outcome", "success"
        ).increment();
    }

    public void duplicateIgnored() {
        meterRegistry.counter(
                "feature.flag.notification.ingestion",
                "outcome", "duplicate"
        ).increment();
    }

    public void leaseRecovery(int count) {
        if (count > 0) {
            meterRegistry.counter(
                    "feature.flag.notification.worker",
                    "operation", "lease-recovered"
            ).increment(count);
        }
    }

    public void deliveryClaimed() {
        meterRegistry.counter(
                "feature.flag.notification.worker",
                "operation", "claimed"
        ).increment();
    }

    public void deliverySucceeded(String mode) {
        delivery("success", mode);
    }

    public void deliveryRetry() {
        delivery("retry", "durable");
    }

    public void deliveryDead(String reason) {
        meterRegistry.counter(
                "feature.flag.notification.delivery",
                "outcome", "terminal-failure",
                "mode", "durable",
                "reason", boundedReason(reason)
        ).increment();
    }

    public void deliveryFailed(String mode) {
        delivery("failure", mode);
    }

    private void delivery(String outcome, String mode) {
        meterRegistry.counter(
                "feature.flag.notification.delivery",
                "outcome", outcome,
                "mode", boundedMode(mode)
        ).increment();
    }

    private double deadCount() {
        try {
            return notificationRepository.countByStatus("DEAD");
        } catch (RuntimeException exception) {
            log.warn(
                    "Notification DEAD gauge query failed; errorType={}",
                    exception.getClass().getSimpleName()
            );
            return Double.NaN;
        }
    }

    private String boundedMode(String mode) {
        return switch (mode) {
            case "durable", "synchronous", "invitation" -> mode;
            default -> "other";
        };
    }

    private String boundedReason(String reason) {
        return "unsupported-channel".equals(reason)
                ? "unsupported-channel"
                : "attempts-exhausted";
    }
}
