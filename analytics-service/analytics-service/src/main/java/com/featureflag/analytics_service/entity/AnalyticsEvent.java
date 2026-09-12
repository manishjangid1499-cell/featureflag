package com.featureflag.analytics_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "analytics_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_analytics_events_dimensions",
                columnNames = {
                        "flag_key",
                        "environment",
                        "event_type"
                }
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String flagKey;

    private String environment;

    private String eventType;

    @Builder.Default
    @Column(nullable = false)
    private Long count = 0L;
}
