package com.featureflag.analytics_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "analytics_events")
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

    private Long count;
}