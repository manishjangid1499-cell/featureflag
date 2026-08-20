package com.featureflag.analytics_service.kafka;

import com.featureflag.analytics_service.event.FlagEvent;
import com.featureflag.analytics_service.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsEventConsumerTest {

    @Mock
    private AnalyticsService analyticsService;

    @InjectMocks
    private AnalyticsEventConsumer consumer;

    @Test
    void processingFailurePropagatesToKafkaContainer() {
        FlagEvent event = new FlagEvent();
        event.setEventType("UPDATED");
        event.setFlagKey("checkout");
        event.setTimestamp("2026-08-20T10:00:00Z");

        when(
                analyticsService.processEvent(
                        "checkout",
                        "UPDATED"
                )
        ).thenThrow(
                new RuntimeException("database unavailable")
        );

        assertThatThrownBy(
                () -> consumer.consume(event)
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessage("database unavailable");
    }
}
