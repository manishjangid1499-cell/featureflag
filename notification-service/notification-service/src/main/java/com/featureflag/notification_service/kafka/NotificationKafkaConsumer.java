package com.featureflag.notification_service.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.notification_service.dto.NotificationEvent;
import com.featureflag.notification_service.dto.NotificationRequest;
import com.featureflag.notification_service.entity.ProcessedEvent;
import com.featureflag.notification_service.repository.ProcessedEventRepository;
import com.featureflag.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationKafkaConsumer {

    private static final String TOPIC =
            "notification-events";

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository
            processedEventRepository;

    @KafkaListener(
            topics = TOPIC,
            groupId = "notification-service-group"
    )
    @Transactional
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

            NotificationRequest request =
                    new NotificationRequest();

            request.setRecipient(event.getRecipient());
            request.setCreatorEmail(
                    event.getCreatorEmail()
            );
            request.setSubject(event.getSubject());
            request.setMessage(event.getMessage());
            request.setType(
                    event.getType() != null
                            ? event.getType()
                            : "EMAIL"
            );

            notificationService.createNotification(
                    request
            );
        } else {
            notificationService.sendToRoleRecipients(
                    event.getSubject(),
                    event.getMessage(),
                    event.getType() != null
                            ? event.getType()
                            : "EMAIL",
                    List.of("OWNER", "ADMIN")
            );
        }

        processedEventRepository.save(
                ProcessedEvent.builder()
                        .eventId(eventId)
                        .topic(TOPIC)
                        .processedAt(LocalDateTime.now())
                        .build()
        );

        log.info(
                "Notification event processed; eventId={}",
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
