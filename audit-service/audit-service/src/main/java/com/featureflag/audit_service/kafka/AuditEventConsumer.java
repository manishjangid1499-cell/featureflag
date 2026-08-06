package com.featureflag.audit_service.kafka;

import com.featureflag.audit_service.entity.AuditLog;
import com.featureflag.audit_service.event.FlagEvent;
import com.featureflag.audit_service.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
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

        System.out.println(
                "Audit Event Saved: "
                        + event.getEventType()
                        + " - "
                        + event.getFlagKey()
        );
    }
}