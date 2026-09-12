package com.featureflag.notification_service.observability;

import com.featureflag.notification_service.repository.NotificationRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationMetricsTest {

    @Test
    void terminalDeliveryAndDeadGaugeAreVisibleWithBoundedTags() {
        NotificationRepository repository = mock(NotificationRepository.class);
        when(repository.countByStatus("DEAD")).thenReturn(4L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NotificationMetrics metrics = new NotificationMetrics(
                registry,
                repository
        );

        metrics.deliveryDead("provider-specific-secret-reason");
        metrics.deliverySucceeded("durable");
        metrics.deliveryRetry();
        metrics.deliveryClaimed();
        metrics.leaseRecovery(2);

        assertThat(registry.get("feature.flag.notification.delivery.dead")
                .gauge().value()).isEqualTo(4.0);
        assertThat(registry.counter(
                "feature.flag.notification.delivery",
                "outcome", "terminal-failure",
                "mode", "durable",
                "reason", "attempts-exhausted"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                "feature.flag.notification.delivery",
                "outcome", "success",
                "mode", "durable"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                "feature.flag.notification.delivery",
                "outcome", "retry",
                "mode", "durable"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                "feature.flag.notification.worker",
                "operation", "claimed"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                "feature.flag.notification.worker",
                "operation", "lease-recovered"
        ).count()).isEqualTo(2.0);
    }
}
