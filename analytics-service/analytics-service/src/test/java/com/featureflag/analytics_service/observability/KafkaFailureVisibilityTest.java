package com.featureflag.analytics_service.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaFailureVisibilityTest {

    @Test
    void retryAndDltSignalsUseOnlyBoundedTopicAndCategoryTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KafkaFailureVisibility visibility = new KafkaFailureVisibility(registry);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "customer-created-arbitrary-topic",
                1,
                2,
                "arbitrary-key",
                "payload"
        );

        visibility.recordFailure(record, new IllegalArgumentException("bad"), 3);
        visibility.dltDestination(
                record,
                new IllegalArgumentException("bad"),
                "feature-flag-events-analytics-dlt"
        );

        assertThat(registry.counter(
                "kafka.consumer.failures",
                "service", "analytics-service",
                "topic", "other",
                "category", "validation"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                "kafka.consumer.dlt",
                "service", "analytics-service",
                "topic", "other",
                "category", "validation"
        ).count()).isEqualTo(1.0);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags())
                        .noneMatch(tag -> tag.getValue().contains("arbitrary-key"))
        );
    }

    @Test
    void successfulProcessingDoesNotCreateFailureMeters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new KafkaFailureVisibility(registry);

        assertThat(registry.getMeters()).isEmpty();
    }
}
