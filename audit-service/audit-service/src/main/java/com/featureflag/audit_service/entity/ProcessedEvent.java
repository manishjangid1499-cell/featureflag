package com.featureflag.audit_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_kafka_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    @Id
    @Column(
            name = "event_id",
            nullable = false,
            updatable = false,
            length = 64
    )
    private String eventId;

    @Column(nullable = false, length = 120)
    private String topic;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
