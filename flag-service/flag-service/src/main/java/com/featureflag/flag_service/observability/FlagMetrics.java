package com.featureflag.flag_service.observability;

import com.featureflag.flag_service.entity.OutboxEvent;
import com.featureflag.flag_service.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;

@Component
@Slf4j
public class FlagMetrics {

    private final MeterRegistry meterRegistry;
    private final OutboxEventRepository outboxEventRepository;

    public FlagMetrics(
            MeterRegistry meterRegistry,
            OutboxEventRepository outboxEventRepository
    ) {
        this.meterRegistry = meterRegistry;
        this.outboxEventRepository = outboxEventRepository;

        Gauge.builder("feature.flag.outbox.oldest.pending.age", this, FlagMetrics::oldestPendingAge)
                .baseUnit("seconds").description("Age of the oldest pending outbox row")
                .register(meterRegistry);

        Gauge.builder(
                        "feature.flag.outbox.pending",
                        this,
                        metrics -> metrics.countOutbox(
                                OutboxEvent.STATUS_PENDING
                        )
                )
                .description("Pending transactional outbox rows")
                .register(meterRegistry);
        Gauge.builder(
                        "feature.flag.outbox.dead",
                        this,
                        metrics -> metrics.countOutbox(
                                OutboxEvent.STATUS_DEAD
                        )
                )
                .description("Dead transactional outbox rows")
                .register(meterRegistry);
    }

    public Timer.Sample startEvaluation() {
        return Timer.start(meterRegistry);
    }

    public void evaluationCompleted(
            Timer.Sample sample,
            boolean enabled
    ) {
        String result = enabled ? "enabled" : "disabled";
        meterRegistry.counter(
                "feature.flag.runtime.evaluations",
                "result", result
        ).increment();
        sample.stop(Timer.builder(
                        "feature.flag.runtime.evaluation.latency"
                )
                .tag("result", result)
                .register(meterRegistry));
    }

    public void evaluationFailed(Timer.Sample sample) {
        meterRegistry.counter(
                "feature.flag.runtime.evaluations",
                "result", "failure"
        ).increment();
        sample.stop(Timer.builder(
                        "feature.flag.runtime.evaluation.latency"
                )
                .tag("result", "failure")
                .register(meterRegistry));
    }

    public void sdkAuthenticationFailure(String reason) {
        String boundedReason = "missing".equals(reason)
                ? "missing"
                : "invalid";
        meterRegistry.counter(
                "feature.flag.sdk.authentication.failures",
                "reason", boundedReason
        ).increment();
    }

    public void outboxPublished(String topic) {
        meterRegistry.counter(
                "feature.flag.outbox.publish",
                "outcome", "success",
                "topic", boundedTopic(topic)
        ).increment();
    }

    public void outboxPublishFailed(String topic) {
        meterRegistry.counter(
                "feature.flag.outbox.publish",
                "outcome", "failure",
                "topic", boundedTopic(topic)
        ).increment();
    }

    public void outboxMarkedDead(String topic) {
        meterRegistry.counter(
                "feature.flag.outbox.dead.transitions",
                "topic", boundedTopic(topic)
        ).increment();
    }

    public void outboxRetentionDeleted(int count) {
        if (count > 0) {
            meterRegistry.counter(
                    "feature.flag.outbox.retention.deleted"
            ).increment(count);
        }
    }

    public void telemetryPublished(boolean successful) {
        meterRegistry.counter(
                "feature.flag.telemetry.publish",
                "outcome", successful ? "success" : "failure"
        ).increment();
    }

    public void telemetryAdmitted(boolean accepted) {
        meterRegistry.counter("feature.flag.telemetry.admission",
                "outcome", accepted ? "accepted" : "dropped").increment();
    }

    private double oldestPendingAge() {
        try {
            Instant oldest = outboxEventRepository.findOldestPendingCreatedAt();
            return oldest == null ? 0 : Math.max(0, Duration.between(oldest, Instant.now()).toSeconds());
        } catch (RuntimeException exception) {
            log.warn("Outbox age query failed; errorType={}", exception.getClass().getSimpleName());
            return Double.NaN;
        }
    }

    private double countOutbox(String status) {
        try {
            return outboxEventRepository.countByStatus(status);
        } catch (RuntimeException exception) {
            log.warn(
                    "Outbox gauge query failed; status={} errorType={}",
                    status,
                    exception.getClass().getSimpleName()
            );
            return Double.NaN;
        }
    }

    private String boundedTopic(String topic) {
        if ("feature-flag-events".equals(topic)) {
            return "feature-flag-events";
        }
        if ("notification-events".equals(topic)) {
            return "notification-events";
        }
        return "other";
    }
}
