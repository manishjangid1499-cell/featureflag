package com.featureflag.audit_service.kafka;

import com.featureflag.audit_service.entity.AuditLog;
import com.featureflag.audit_service.event.FlagEvent;
import com.featureflag.audit_service.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditEventConsumer {

    private final AuditLogRepository auditLogRepository;

    @KafkaListener(
            topics = "feature-flag-events",
            groupId = "audit-group"
    )
    public void consume(FlagEvent event) {
        AuditLog auditLog = AuditLog.builder()
                .eventType(event.getEventType())
                .flagKey(event.getFlagKey())
                .timestamp(event.getTimestamp())
                .build();

        auditLogRepository.save(auditLog);

        log.info(
                "Audit event persisted: eventType={} flagKey={}",
                event.getEventType(),
                event.getFlagKey()
        );
    }
}
