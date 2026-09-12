package com.featureflag.audit_service.dto;

import com.featureflag.audit_service.entity.AuditLog;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        String eventId,
        String eventType,
        String flagKey,
        String environment,
        String timestamp,
        String sourceService,
        String actor,
        String beforeState,
        String afterState,
        Instant occurredAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getEventId(),
                log.getEventType(),
                log.getFlagKey(),
                log.getEnvironment(),
                log.getTimestamp(),
                log.getSourceService(),
                log.getActor(),
                log.getBeforeState(),
                log.getAfterState(),
                log.getOccurredAt()
        );
    }
}
