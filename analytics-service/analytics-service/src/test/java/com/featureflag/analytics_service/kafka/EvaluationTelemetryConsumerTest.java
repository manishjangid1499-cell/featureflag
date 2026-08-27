package com.featureflag.analytics_service.kafka;

import com.featureflag.analytics_service.entity.AnalyticsEvent;
import com.featureflag.analytics_service.entity.ProcessedEvent;
import com.featureflag.analytics_service.event.FlagEvent;
import com.featureflag.analytics_service.repository.ProcessedEventRepository;
import com.featureflag.analytics_service.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.annotation.KafkaListener;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationTelemetryConsumerTest {

    private static final String TOPIC =
            "feature-flag-evaluations";

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private EvaluationTelemetryConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new EvaluationTelemetryConsumer(
                analyticsService,
                processedEventRepository,
                TOPIC
        );
    }

    @Test
    void subscribesOnlyToDedicatedEvaluationTopic() {
        Set<String> topics = new HashSet<>();
        for (Method method
                : EvaluationTelemetryConsumer.class.getDeclaredMethods()) {
            KafkaListener listener =
                    method.getAnnotation(KafkaListener.class);
            if (listener != null) {
                topics.addAll(Arrays.asList(listener.topics()));
                assertThat(listener.groupId())
                        .isEqualTo("analytics-evaluation-group");
            }
        }

        assertThat(topics).containsExactly(
                "${telemetry.evaluation.topic:"
                        + TOPIC
                        + "}"
        );
    }

    @Test
    void enabledTelemetryUpdatesEnabledAggregateAndStoresMarker() {
        FlagEvent event = event(
                "evaluation-1",
                FlagEvent.EVALUATION_ENABLED
        );
        when(
                analyticsService.processEvent(
                        "checkout",
                        "DEV",
                        FlagEvent.EVALUATION_ENABLED
                )
        ).thenReturn(aggregate(FlagEvent.EVALUATION_ENABLED));

        consumer.consume(event);

        verify(analyticsService).processEvent(
                "checkout",
                "DEV",
                FlagEvent.EVALUATION_ENABLED
        );
        assertStoredMarker("evaluation-1");
    }

    @Test
    void disabledTelemetryUpdatesDisabledAggregateAndStoresMarker() {
        FlagEvent event = event(
                "evaluation-2",
                FlagEvent.EVALUATION_DISABLED
        );
        when(
                analyticsService.processEvent(
                        "checkout",
                        "DEV",
                        FlagEvent.EVALUATION_DISABLED
                )
        ).thenReturn(aggregate(FlagEvent.EVALUATION_DISABLED));

        consumer.consume(event);

        verify(analyticsService).processEvent(
                "checkout",
                "DEV",
                FlagEvent.EVALUATION_DISABLED
        );
        assertStoredMarker("evaluation-2");
    }

    @Test
    void duplicateEventIdIsSkipped() {
        when(
                processedEventRepository.existsById("evaluation-1")
        ).thenReturn(true);

        consumer.consume(event(
                "evaluation-1",
                FlagEvent.EVALUATION_ENABLED
        ));

        verify(analyticsService, never())
                .processEvent(anyString(), anyString(), anyString());
        verify(processedEventRepository, never())
                .save(any(ProcessedEvent.class));
    }

    @Test
    void lifecycleEventTypeIsRejectedBeforeWrites() {
        assertRejectedBeforeWrites(event(
                "evaluation-1",
                "FLAG_UPDATED"
        ));
    }

    @Test
    void missingTimestampIsRejectedBeforeWrites() {
        FlagEvent event = event(
                "evaluation-1",
                FlagEvent.EVALUATION_ENABLED
        );
        event.setTimestamp(" ");
        assertRejectedBeforeWrites(event);
    }

    @Test
    void blankEventIdIsRejectedBeforeWrites() {
        FlagEvent event = event(
                " ",
                FlagEvent.EVALUATION_ENABLED
        );
        assertRejectedBeforeWrites(event);
    }

    @Test
    void missingAggregateDimensionIsRejectedBeforeWrites() {
        FlagEvent event = event(
                "evaluation-1",
                FlagEvent.EVALUATION_ENABLED
        );
        event.setEnvironment(null);
        assertRejectedBeforeWrites(event);
    }

    private void assertStoredMarker(String eventId) {
        ArgumentCaptor<ProcessedEvent> marker =
                ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(marker.capture());
        assertThat(marker.getValue().getEventId())
                .isEqualTo(eventId);
        assertThat(marker.getValue().getTopic())
                .isEqualTo(TOPIC);
        assertThat(marker.getValue().getProcessedAt())
                .isNotNull();
    }

    private void assertRejectedBeforeWrites(FlagEvent event) {
        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalArgumentException.class);
        verify(analyticsService, never())
                .processEvent(anyString(), anyString(), anyString());
        verify(processedEventRepository, never())
                .save(any(ProcessedEvent.class));
    }

    private AnalyticsEvent aggregate(String eventType) {
        return AnalyticsEvent.builder()
                .flagKey("checkout")
                .environment("DEV")
                .eventType(eventType)
                .count(1L)
                .build();
    }

    private FlagEvent event(
            String eventId,
            String eventType
    ) {
        return new FlagEvent(
                eventId,
                eventType,
                "checkout",
                "DEV",
                "2026-08-27T12:00:00Z"
        );
    }
}
