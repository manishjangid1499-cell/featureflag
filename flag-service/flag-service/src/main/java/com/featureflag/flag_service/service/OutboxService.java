package com.featureflag.flag_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.flag_service.dto.NotificationEvent;
import com.featureflag.flag_service.entity.OutboxEvent;
import com.featureflag.flag_service.event.FlagAuditSnapshot;
import com.featureflag.flag_service.event.FlagEvent;
import com.featureflag.flag_service.repository.FeatureFlagRepository;
import com.featureflag.flag_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    public static final String FEATURE_FLAG_TOPIC =
            "feature-flag-events";

    public static final String NOTIFICATION_TOPIC =
            "notification-events";

    public static final String SOURCE_SERVICE =
            "flag-service";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final FeatureFlagRepository featureFlagRepository;
    private final FlagAuditContext flagAuditContext;
    private final Clock clock;

    public String enqueueFlagEvent(
            String eventType,
            String flagKey,
            String environment
    ) {
        String eventId = UUID.randomUUID().toString();

        Instant eventInstant = clock.instant();
        LocalDateTime occurredAt = LocalDateTime.ofInstant(
                eventInstant,
                ZoneOffset.UTC
        );
        FlagAuditContext.AuditDetails auditDetails =
                flagAuditContext.current().orElse(null);

        FlagEvent event = new FlagEvent(
                eventId,
                eventType,
                flagKey,
                environment,
                eventInstant.toString(),
                SOURCE_SERVICE,
                auditDetails == null
                        ? null
                        : auditDetails.actor(),
                auditDetails == null
                        ? null
                        : auditDetails.before(),
                auditDetails == null
                        ? null
                        : resolveAfter(
                                eventType,
                                flagKey,
                                environment
                        ),
                occurredAt
        );

        persist(
                eventId,
                FEATURE_FLAG_TOPIC,
                flagKey,
                eventType,
                event
        );

        return eventId;
    }

    private FlagAuditSnapshot resolveAfter(
            String eventType,
            String flagKey,
            String environment
    ) {
        if ("FLAG_DELETED".equals(eventType)) {
            return null;
        }

        return featureFlagRepository
                .findByFlagKeyAndEnvironment(
                        flagKey,
                        environment
                )
                .map(FlagAuditSnapshot::from)
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to capture resulting flag state for "
                                + eventType
                ));
    }

    public String enqueueNotificationEvent(
            String subject,
            String message
    ) {
        String eventId = UUID.randomUUID().toString();

        NotificationEvent event =
                new NotificationEvent(
                        eventId,
                        null,
                        null,
                        subject,
                        message,
                        "EMAIL"
                );

        persist(
                eventId,
                NOTIFICATION_TOPIC,
                eventId,
                "NOTIFICATION",
                event
        );

        return eventId;
    }

    private void persist(
            String eventId,
            String topic,
            String messageKey,
            String eventType,
            Object event
    ) {
        String payload;

        try {
            payload =
                    objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to serialize outbox event",
                    exception
            );
        }

        Instant now = clock.instant();

        OutboxEvent outboxEvent =
                OutboxEvent.builder()
                        .id(eventId)
                        .topic(topic)
                        .messageKey(messageKey)
                        .eventType(eventType)
                        .payload(payload)
                        .status(
                                OutboxEvent.STATUS_PENDING
                        )
                        .attempts(0)
                        .createdAt(now)
                        .nextAttemptAt(now)
                        .build();

        outboxEventRepository.save(outboxEvent);
    }
}
