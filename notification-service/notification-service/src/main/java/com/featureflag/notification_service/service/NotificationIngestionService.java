package com.featureflag.notification_service.service;

import com.featureflag.notification_service.dto.NotificationEvent;
import com.featureflag.notification_service.entity.DeliveryMode;
import com.featureflag.notification_service.entity.Notification;
import com.featureflag.notification_service.entity.ProcessedEvent;
import com.featureflag.notification_service.repository.NotificationRepository;
import com.featureflag.notification_service.repository.ProcessedEventRepository;
import com.featureflag.notification_service.validation.NotificationTypePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationIngestionService {

    private static final String NOTIFICATION_TOPIC =
            "notification-events";

    private final NotificationRepository notificationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final Clock clock;

    @Transactional
    public void ingestDirectNotificationEvent(
            String eventId,
            NotificationEvent event
    ) {
        ingestNotificationEvent(
                eventId,
                event,
                List.of(event.getRecipient().trim()),
                normalizeNullableEmail(event.getCreatorEmail())
        );
    }

    @Transactional
    public void ingestRoleNotificationEvent(
            String eventId,
            NotificationEvent event,
            List<String> recipients
    ) {
        ingestNotificationEvent(
                eventId,
                event,
                recipients,
                null
        );
    }

    private void ingestNotificationEvent(
            String eventId,
            NotificationEvent event,
            List<String> recipients,
            String creatorEmail
    ) {
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String type = NotificationTypePolicy.requireExplicitEmail(
                event.getType()
        );

        Instant now = clock.instant();

        List<Notification> notifications = recipients.stream()
                .map(recipient -> Notification.builder()
                        .recipient(recipient)
                        .creatorEmail(creatorEmail)
                        .subject(event.getSubject())
                        .message(event.getMessage())
                        .type(type)
                        .status("PENDING")
                        .deliveryMode(DeliveryMode.DURABLE)
                        .attemptCount(0)
                        .createdAt(now)
                        .nextAttemptAt(now)
                        .build())
                .toList();

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }

        processedEventRepository.save(
                ProcessedEvent.builder()
                        .eventId(eventId)
                        .topic(NOTIFICATION_TOPIC)
                        .processedAt(now)
                        .build()
        );
    }

    private String normalizeNullableEmail(String email) {
        return email == null ? null : email.trim();
    }
}
