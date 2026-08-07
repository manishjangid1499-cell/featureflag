package com.featureflag.notification_service.kafka;

import com.featureflag.notification_service.dto.NotificationRequest;
import com.featureflag.notification_service.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

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
    public void consumeNotificationEvent(String message) {

        try {
            log.info("Received notification event: {}", message);

            NotificationRequest request =
                    objectMapper.readValue(message, NotificationRequest.class);

            notificationService.createNotification(request);

            log.info("Notification saved successfully");

        } catch (Exception e) {
            log.error("Failed to process notification event", e);
        }
    }
}