package com.featureflag.flag_service.observability;

import com.featureflag.flag_service.entity.OutboxEvent;
import com.featureflag.flag_service.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlagMetricsTest {

    @Test
    void runtimeOutboxAndAuthenticationSignalsUseBoundedTags() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        when(repository.countByStatus(OutboxEvent.STATUS_PENDING)).thenReturn(2L);
        when(repository.countByStatus(OutboxEvent.STATUS_DEAD)).thenReturn(3L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FlagMetrics metrics = new FlagMetrics(registry, repository);

        Timer.Sample sample = metrics.startEvaluation();
        metrics.evaluationCompleted(sample, true);
        metrics.sdkAuthenticationFailure("attacker-controlled-reason");
        metrics.outboxMarkedDead("attacker-controlled-topic");
        metrics.outboxRetentionDeleted(4);

        assertThat(registry.counter(
                "feature.flag.runtime.evaluations",
                "result", "enabled"
        ).count()).isEqualTo(1.0);
        assertThat(registry.get("feature.flag.runtime.evaluation.latency")
                .tag("result", "enabled").timer().count()).isEqualTo(1);
        assertThat(registry.counter(
                "feature.flag.sdk.authentication.failures",
                "reason", "invalid"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                "feature.flag.outbox.dead.transitions",
                "topic", "other"
        ).count()).isEqualTo(1.0);
        assertThat(registry.get("feature.flag.outbox.pending")
                .gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("feature.flag.outbox.dead")
                .gauge().value()).isEqualTo(3.0);
        assertThat(registry.counter(
                "feature.flag.outbox.retention.deleted"
        ).count()).isEqualTo(4.0);
    }
}
