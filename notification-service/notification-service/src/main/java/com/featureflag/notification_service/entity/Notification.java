package com.featureflag.notification_service.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String recipient;

    private String creatorEmail;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant sentAt;

    @JsonIgnore
    @Builder.Default
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "delivery_mode", length = 20)
    private DeliveryMode deliveryMode = DeliveryMode.SYNCHRONOUS;

    @JsonIgnore
    @Builder.Default
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Column(name = "attempt_count")
    private Integer attemptCount = 0;

    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Column(name = "lease_until")
    private Instant leaseUntil;

    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Column(name = "claim_token", length = 36)
    private String claimToken;

    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Column(name = "last_error_type", length = 128)
    private String lastErrorType;
}
