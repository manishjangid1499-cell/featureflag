package com.featureflag.flag_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.flag_service.client.AuthRecipientsClient;
import com.featureflag.flag_service.dto.NotificationEvent;
import com.featureflag.flag_service.entity.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OutboxPayloadEnricher {

    private static final List<String> CONTROL_PLANE_ROLES =
            List.of("OWNER", "ADMIN");

    private final ObjectMapper objectMapper;
    private final AuthRecipientsClient authRecipientsClient;

    public String enrich(OutboxEvent event) {
        if (!OutboxService.NOTIFICATION_TOPIC.equals(event.getTopic())) {
            return event.getPayload();
        }

        NotificationEvent notification = readNotification(event.getPayload());
        if (hasText(notification.getRecipient())
                || notification.getRecipients() != null) {
            return event.getPayload();
        }

        List<String> recipients = normalizeRecipients(
                authRecipientsClient.getNotificationRecipients(
                        CONTROL_PLANE_ROLES
                )
        );
        notification.setRecipients(recipients);

        String enrichedPayload = writeNotification(notification);
        event.setPayload(enrichedPayload);
        return enrichedPayload;
    }

    private NotificationEvent readNotification(String payload) {
        try {
            return objectMapper.readValue(payload, NotificationEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to read notification outbox payload",
                    exception
            );
        }
    }

    private String writeNotification(NotificationEvent notification) {
        try {
            return objectMapper.writeValueAsString(notification);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to enrich notification outbox payload",
                    exception
            );
        }
    }

    private List<String> normalizeRecipients(List<String> recipients) {
        if (recipients == null) {
            throw new IllegalStateException(
                    "Auth Service returned no recipient collection"
            );
        }

        return recipients.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(this::hasText)
                .map(email -> email.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
