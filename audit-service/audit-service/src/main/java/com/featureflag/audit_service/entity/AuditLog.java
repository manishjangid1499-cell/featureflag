package com.featureflag.audit_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "audit_logs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_audit_logs_event_id",
                columnNames = "event_id"
        ),
        indexes = @Index(
                name = "idx_audit_logs_flag_occurred_at",
                columnList = "flag_key, occurred_at, id"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 64)
    private String eventId;

    private String eventType;

    private String flagKey;
    private String environment;

    private String timestamp;

    @Column(name = "source_service", length = 100)
    private String sourceService;

    private String actor;

    @Lob
    @Column(name = "before_state", columnDefinition = "LONGTEXT")
    private String beforeState;

    @Lob
    @Column(name = "after_state", columnDefinition = "LONGTEXT")
    private String afterState;

    @Column(name = "occurred_at")
    private java.time.LocalDateTime occurredAt;
}
