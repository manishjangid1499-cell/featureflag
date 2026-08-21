package com.featureflag.audit_service.kafka;

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

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditEventConsumer {

    private static final String TOPIC =
            "feature-flag-events";

    private final AuditLogRepository auditLogRepository;
    private final ProcessedEventRepository
            processedEventRepository;

    @KafkaListener(
            topics = TOPIC,
            groupId = "audit-group"
    )
    @Transactional
    public void consume(FlagEvent event) {
        String eventId =
                requireEventId(event.getEventId());

        if (processedEventRepository.existsById(eventId)) {
            log.info(
                    "Skipping duplicate audit event; eventId={}",
                    eventId
            );
            return;
        }

        AuditLog auditLog = AuditLog.builder()
                .eventType(event.getEventType())
                .flagKey(event.getFlagKey())
                .environment(event.getEnvironment())
                .timestamp(event.getTimestamp())
                .build();

        auditLogRepository.save(auditLog);

        processedEventRepository.save(
                ProcessedEvent.builder()
                        .eventId(eventId)
                        .topic(TOPIC)
                        .processedAt(LocalDateTime.now())
                        .build()
        );

        log.info(
                "Audit event persisted; eventId={} "
                        + "eventType={} flagKey={}",
                eventId,
                event.getEventType(),
                event.getFlagKey()
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
