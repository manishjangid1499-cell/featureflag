package com.featureflag.audit_service.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditMetricsTest {

    @Test
    void persistenceAndDuplicateCountersHaveOnlyBoundedOutcomeTag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditMetrics metrics = new AuditMetrics(registry);

        metrics.eventPersisted();
        metrics.duplicateIgnored();

        assertThat(registry.counter(
                "feature.flag.audit.events",
                "outcome", "persisted"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                "feature.flag.audit.events",
                "outcome", "duplicate"
        ).count()).isEqualTo(1.0);
    }
}
