package com.featureflag.audit_service.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.audit_service.entity.AuditLog;
import com.featureflag.audit_service.entity.ProcessedEvent;
import com.featureflag.audit_service.event.FlagEvent;
import com.featureflag.audit_service.repository.AuditLogRepository;
import com.featureflag.audit_service.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditEventConsumer {

    private static final String TOPIC =
            "feature-flag-events";

    private static final Set<String> LIFECYCLE_EVENT_TYPES =
            Set.of(
                    "FLAG_CREATED",
                    "FLAG_UPDATED",
                    "FLAG_TOGGLED",
                    "FLAG_DELETED"
            );

    private final AuditLogRepository auditLogRepository;
    private final ProcessedEventRepository
            processedEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = TOPIC,
            groupId = "audit-group"
    )
    @Transactional
    public void consume(FlagEvent event) {
        String eventId =
                requireEventId(event.getEventId());

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
        String timestamp = requireField(
                event.getTimestamp(),
                "timestamp"
        );

        if (!LIFECYCLE_EVENT_TYPES.contains(eventType)) {
            throw new IllegalArgumentException(
                    "Unsupported audit eventType: " + eventType
            );
        }

        if (processedEventRepository.existsById(eventId)) {
            log.info(
                    "Skipping duplicate audit event; eventId={}",
                    eventId
            );
            return;
        }

        AuditLog auditLog = AuditLog.builder()
                .eventId(eventId)
                .eventType(eventType)
                .flagKey(flagKey)
                .environment(environment)
                .timestamp(timestamp)
                .sourceService(optional(event.getSourceService()))
                .actor(optional(event.getActor()))
                .beforeState(toJson(event.getBefore()))
                .afterState(toJson(event.getAfter()))
                .occurredAt(resolveOccurredAt(event, timestamp))
                .build();

        auditLogRepository.save(auditLog);

        processedEventRepository.save(
                ProcessedEvent.builder()
                        .eventId(eventId)
                        .topic(TOPIC)
                        .processedAt(Instant.now())
                        .build()
        );

        log.info(
                "Audit event persisted; eventId={} "
                        + "eventType={} flagKey={}",
                eventId,
                eventType,
                event.getFlagKey()
        );
    }

    private String toJson(Object snapshot) {
        if (snapshot == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Unable to serialize flag audit snapshot",
                    exception
            );
        }
    }

    private Instant resolveOccurredAt(
            FlagEvent event,
            String timestamp
    ) {
        if (event.getOccurredAt() != null) {
            return event.getOccurredAt().toInstant(ZoneOffset.UTC);
        }

        try {
            return Instant.parse(timestamp);
        } catch (DateTimeParseException instantFailure) {
            try {
                return OffsetDateTime.parse(timestamp)
                        .toInstant();
            } catch (DateTimeParseException offsetFailure) {
                try {
                    return LocalDateTime.parse(timestamp)
                            .toInstant(ZoneOffset.UTC);
                } catch (DateTimeParseException localFailure) {
                    log.warn(
                            "Audit event timestamp could not be converted; "
                                    + "eventId={} errorType={}",
                            event.getEventId(),
                            localFailure.getClass().getSimpleName()
                    );
                    return null;
                }
            }
        }
    }

    private String optional(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
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
        return value.trim();
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
