package com.featureflag.notification_service.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.notification_service.dto.NotificationEvent;
import com.featureflag.notification_service.dto.NotificationRequest;
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

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "notification-events",
            groupId = "notification-service-group"
    )
    public void consumeNotificationEvent(String message)
            throws JsonProcessingException {

        log.info("Received notification event");

        NotificationEvent event =
                objectMapper.readValue(
                        message,
                        NotificationEvent.class
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

        log.info(
                "Notification event processed successfully"
        );
    }
}
