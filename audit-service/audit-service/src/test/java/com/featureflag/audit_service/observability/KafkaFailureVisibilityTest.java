package com.featureflag.audit_service.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaFailureVisibilityTest {

    @Test
    void retryExhaustionCreatesBoundedFailureAndDltMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KafkaFailureVisibility visibility = new KafkaFailureVisibility(registry);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "feature-flag-events", 0, 1, "event-key", "value"
        );

        visibility.recordFailure(record, new IllegalStateException("failed"), 3);
        visibility.dltDestination(
                record,
                new IllegalStateException("failed"),
                "feature-flag-events-audit-dlt"
        );

        assertThat(registry.counter(
                "kafka.consumer.failures",
                "service", "audit-service",
                "topic", "feature-flag-events",
                "category", "processing"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                "kafka.consumer.dlt",
                "service", "audit-service",
                "topic", "feature-flag-events",
                "category", "processing"
        ).count()).isEqualTo(1.0);
    }
}
