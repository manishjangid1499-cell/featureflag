package com.featureflag.analytics_service.kafka;

import com.featureflag.analytics_service.entity.AnalyticsEvent;
import com.featureflag.analytics_service.entity.ProcessedEvent;
import com.featureflag.analytics_service.event.FlagEvent;
import com.featureflag.analytics_service.repository.ProcessedEventRepository;
import com.featureflag.analytics_service.observability.AnalyticsMetrics;
import com.featureflag.analytics_service.service.AnalyticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Service
@Slf4j
public class EvaluationTelemetryConsumer {

    public static final String DEFAULT_TOPIC =
            "feature-flag-evaluations";

    private static final Set<String> EVALUATION_EVENT_TYPES =
            Set.of(
                    FlagEvent.EVALUATION_ENABLED,
                    FlagEvent.EVALUATION_DISABLED
            );

    private final AnalyticsService analyticsService;
    private final ProcessedEventRepository processedEventRepository;
    private final String topic;
    private final AnalyticsMetrics analyticsMetrics;

    public EvaluationTelemetryConsumer(
            AnalyticsService analyticsService,
            ProcessedEventRepository processedEventRepository,
            @Value(
                    "${telemetry.evaluation.topic:"
                            + DEFAULT_TOPIC
                            + "}"
            )
            String topic,
            AnalyticsMetrics analyticsMetrics
    ) {
        this.analyticsService = analyticsService;
        this.processedEventRepository = processedEventRepository;
        this.topic = topic;
        this.analyticsMetrics = analyticsMetrics;
    }

    @KafkaListener(
            topics =
                    "${telemetry.evaluation.topic:"
                            + DEFAULT_TOPIC
                            + "}",
            groupId = "analytics-evaluation-group"
    )
    @Transactional
    public void consume(FlagEvent event) {
        String eventId = requireEventId(event.getEventId());
        String eventType = requireField(
                event.getEventType(),
                "eventType"
        );
        String flagKey = requireField(
                event.getFlagKey(),
                "flagKey"
        );
        String environment = requireField(
                event.getEnvironment(),
                "environment"
        );
        requireField(event.getTimestamp(), "timestamp");

        if (!EVALUATION_EVENT_TYPES.contains(eventType)) {
            throw new IllegalArgumentException(
                    "Unsupported evaluation eventType: "
                            + eventType
            );
        }

        if (processedEventRepository.existsById(eventId)) {
            analyticsMetrics.duplicateIgnored(eventType);
            log.info(
                    "Skipping duplicate evaluation telemetry; "
                            + "eventId={}",
                    eventId
            );
            return;
        }

        AnalyticsEvent analyticsEvent =
                analyticsService.processEvent(
                        flagKey,
                        environment,
                        eventType
                );

        processedEventRepository.save(
                ProcessedEvent.builder()
                        .eventId(eventId)
                        .topic(topic)
                        .processedAt(Instant.now())
                        .build()
        );

        log.info(
                "Evaluation telemetry processed; eventId={} "
                        + "flagKey={} environment={} eventType={} "
                        + "count={}",
                eventId,
                flagKey,
                environment,
                eventType,
                analyticsEvent.getCount()
        );
    }

    private String requireField(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Evaluation telemetry "
                            + fieldName
                            + " is required"
            );
        }
        return value;
    }

    private String requireEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException(
                    "Evaluation telemetry eventId is required"
            );
        }
        return eventId.trim();
    }
}
