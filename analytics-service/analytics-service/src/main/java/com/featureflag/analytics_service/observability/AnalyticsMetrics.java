package com.featureflag.analytics_service.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsMetrics {

    private final MeterRegistry meterRegistry;

    public AnalyticsMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void ingestionSucceeded(String eventType) {
        meterRegistry.counter(
                "feature.flag.analytics.ingestion",
                "stream", stream(eventType),
                "outcome", "success"
        ).increment();
    }

    public void aggregationFailed(String eventType) {
        meterRegistry.counter(
                "feature.flag.analytics.aggregation.failures",
                "stream", stream(eventType)
        ).increment();
    }

    public void duplicateIgnored(String eventType) {
        meterRegistry.counter(
                "feature.flag.analytics.duplicates",
                "stream", stream(eventType)
        ).increment();
    }

    private String stream(String eventType) {
        return eventType != null
                && eventType.startsWith("EVALUATION_")
                ? "evaluation"
                : "lifecycle";
    }
}
