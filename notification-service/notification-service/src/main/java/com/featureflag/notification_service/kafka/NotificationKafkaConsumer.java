package com.featureflag.notification_service.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.notification_service.dto.NotificationEvent;
import com.featureflag.notification_service.observability.NotificationMetrics;
import com.featureflag.notification_service.repository.ProcessedEventRepository;
import com.featureflag.notification_service.service.NotificationIngestionService;
import com.featureflag.notification_service.validation.NotificationTypePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationKafkaConsumer {

    private static final String TOPIC =
            "notification-events";

    private final NotificationIngestionService
            notificationIngestionService;
    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository
            processedEventRepository;
    private final NotificationMetrics notificationMetrics;

    @KafkaListener(
            topics = TOPIC,
            groupId = "notification-service-group"
    )
    public void consumeNotificationEvent(String message)
            throws JsonProcessingException {

        NotificationEvent event =
                objectMapper.readValue(
                        message,
                        NotificationEvent.class
                );

        String eventId =
                requireEventId(event.getEventId());

        if (processedEventRepository.existsById(eventId)) {
            notificationMetrics.duplicateIgnored();
            log.info(
                    "Skipping duplicate notification event; "
                            + "eventId={}",
                    eventId
            );
            return;
        }

        event.setType(
                NotificationTypePolicy.resolveKafkaType(
                        event.getType()
                )
        );

        log.info(
                "Received notification event; eventId={}",
                eventId
        );

        if (event.getRecipient() != null
                && !event.getRecipient().isBlank()) {
            notificationIngestionService
                    .ingestDirectNotificationEvent(
                            eventId,
                            event
                    );
        } else {
            List<String> recipients = requireRecipients(event);

            notificationIngestionService
                    .ingestRoleNotificationEvent(
                            eventId,
                            event,
                            recipients
                    );
        }

        log.info(
                "Notification event ingestion completed; "
                        + "eventId={}",
                eventId
        );
        notificationMetrics.eventIngested();
    }

    private List<String> requireRecipients(NotificationEvent event) {
        if (event.getRecipients() == null) {
            throw new IllegalArgumentException(
                    "Kafka notification recipients are required"
            );
        }

        return event.getRecipients().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(recipient -> !recipient.isBlank())
                .map(recipient -> recipient.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
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
