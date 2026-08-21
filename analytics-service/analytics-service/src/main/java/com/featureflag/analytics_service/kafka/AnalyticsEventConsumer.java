package com.featureflag.analytics_service.kafka;

import com.featureflag.analytics_service.entity.AnalyticsEvent;
import com.featureflag.analytics_service.entity.ProcessedEvent;
import com.featureflag.analytics_service.event.FlagEvent;
import com.featureflag.analytics_service.repository.ProcessedEventRepository;
import com.featureflag.analytics_service.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsEventConsumer {

    private static final String TOPIC =
            "feature-flag-events";

    private final AnalyticsService analyticsService;
    private final ProcessedEventRepository
            processedEventRepository;

    @KafkaListener(
            topics = TOPIC,
            groupId = "analytics-group"
    )
    @Transactional
    public void consume(FlagEvent event) {
        String eventId =
                requireEventId(event.getEventId());

        requireField(
                event.getEventType(),
                "eventType"
        );
        requireField(
                event.getFlagKey(),
                "flagKey"
        );
        requireField(
                event.getEnvironment(),
                "environment"
        );

        if (processedEventRepository.existsById(eventId)) {
            log.info(
                    "Skipping duplicate analytics event; "
                            + "eventId={}",
                    eventId
            );
            return;
        }

        log.info(
                "Received feature flag event; "
                        + "eventId={} eventType={} flagKey={}",
                eventId,
                event.getEventType(),
                event.getFlagKey()
        );

        AnalyticsEvent analyticsEvent =
                analyticsService.processEvent(
                        event.getFlagKey(),
                        event.getEnvironment(),
                        event.getEventType()
                );

        processedEventRepository.save(
                ProcessedEvent.builder()
                        .eventId(eventId)
                        .topic(TOPIC)
                        .processedAt(LocalDateTime.now())
                        .build()
        );

        log.info(
                "Analytics updated; eventId={} "
                        + "flagKey={} count={}",
                eventId,
                event.getFlagKey(),
                analyticsEvent.getCount()
        );
    }

    private String requireField(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Kafka "
                            + fieldName
                            + " is required"
            );
        }
        return value;
    }

    private String requireEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException(
                    "Kafka eventId is required"
            );
        }

        return eventId.trim();
    }
}
