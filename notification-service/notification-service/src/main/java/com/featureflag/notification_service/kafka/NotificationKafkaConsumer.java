package com.featureflag.notification_service.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.notification_service.dto.NotificationEvent;
import com.featureflag.notification_service.repository.ProcessedEventRepository;
import com.featureflag.notification_service.service.NotificationIngestionService;
import com.featureflag.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationKafkaConsumer {

    private static final String TOPIC =
            "notification-events";

    private final NotificationService notificationService;
    private final NotificationIngestionService
            notificationIngestionService;
    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository
            processedEventRepository;

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
            log.info(
                    "Skipping duplicate notification event; "
                            + "eventId={}",
                    eventId
            );
            return;
        }

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
            List<String> recipients =
                    notificationService
                            .resolveRoleRecipientEmails(
                                    List.of(
                                            "OWNER",
                                            "ADMIN"
                                    )
                            );

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
