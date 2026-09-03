package com.featureflag.audit_service.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AuditMetrics {

    private final MeterRegistry meterRegistry;

    public AuditMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void eventPersisted() {
        meterRegistry.counter(
                "feature.flag.audit.events",
                "outcome", "persisted"
        ).increment();
    }

    public void duplicateIgnored() {
        meterRegistry.counter(
                "feature.flag.audit.events",
                "outcome", "duplicate"
        ).increment();
    }
}
