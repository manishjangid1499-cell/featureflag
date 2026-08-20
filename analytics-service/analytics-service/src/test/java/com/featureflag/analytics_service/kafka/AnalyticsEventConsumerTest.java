package com.featureflag.analytics_service.kafka;

import com.featureflag.analytics_service.entity.AnalyticsEvent;
import com.featureflag.analytics_service.entity.ProcessedEvent;
import com.featureflag.analytics_service.event.FlagEvent;
import com.featureflag.analytics_service.repository.ProcessedEventRepository;
import com.featureflag.analytics_service.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsEventConsumerTest {

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private ProcessedEventRepository
            processedEventRepository;

    @InjectMocks
    private AnalyticsEventConsumer consumer;

    @Test
    void successfulEventUpdatesAnalyticsAndStoresMarker() {
        FlagEvent event = event();

        when(
                analyticsService.processEvent(
                        "checkout",
                        "UPDATED"
                )
        ).thenReturn(
                AnalyticsEvent.builder()
                        .flagKey("checkout")
                        .eventType("UPDATED")
                        .count(1L)
                        .build()
        );

        consumer.consume(event);

        verify(analyticsService).processEvent(
                "checkout",
                "UPDATED"
        );

        verify(processedEventRepository).save(
                any(ProcessedEvent.class)
        );
    }

    @Test
    void duplicateEventIsSkipped() {
        when(
                processedEventRepository.existsById(
                        "event-1"
                )
        ).thenReturn(true);

        consumer.consume(event());

        verify(
                analyticsService,
                never()
        ).processEvent(any(), any());

        verify(
                processedEventRepository,
                never()
        ).save(any(ProcessedEvent.class));
    }

    @Test
    void processingFailurePropagatesWithoutMarker() {
        when(
                analyticsService.processEvent(
                        "checkout",
                        "UPDATED"
                )
        ).thenThrow(
                new RuntimeException(
                        "database unavailable"
                )
        );

        assertThatThrownBy(
                () -> consumer.consume(event())
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessage("database unavailable");

        verify(
                processedEventRepository,
                never()
        ).save(any(ProcessedEvent.class));
    }

    @Test
    void missingEventIdIsRejected() {
        FlagEvent event = event();
        event.setEventId(" ");

        assertThatThrownBy(
                () -> consumer.consume(event)
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "Kafka eventId is required"
                );

        verify(
                analyticsService,
                never()
        ).processEvent(any(), any());
    }

    private FlagEvent event() {
        FlagEvent event = new FlagEvent();
        event.setEventId("event-1");
        event.setEventType("UPDATED");
        event.setFlagKey("checkout");
        event.setTimestamp(
                "2026-08-20T10:00:00Z"
        );
        return event;
    }
}
