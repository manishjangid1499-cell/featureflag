package com.featureflag.analytics_service.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsMetricsTest {

    @Test
    void ingestionAndAggregationMetricsUseBoundedStreamTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AnalyticsMetrics metrics = new AnalyticsMetrics(registry);

        metrics.ingestionSucceeded("EVALUATION_ENABLED");
        metrics.aggregationFailed("customer-controlled-event-name");
        metrics.duplicateIgnored("EVALUATION_DISABLED");

        assertThat(registry.counter(
                "feature.flag.analytics.ingestion",
                "stream", "evaluation",
                "outcome", "success"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                "feature.flag.analytics.duplicates",
                "stream", "evaluation"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                "feature.flag.analytics.aggregation.failures",
                "stream", "lifecycle"
        ).count()).isEqualTo(1.0);
    }
}
