package com.featureflag.flag_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "feature_flags",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_flag_key_environment",
                        columnNames = {
                                "flag_key",
                                "environment"
                        }
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureFlag implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(name = "flag_key", nullable = false)
    private String flagKey;

    private Boolean enabled;

    private String description;

    /**
     * DEV, QA, STAGING, PROD
     */
    @Column(nullable = false)
    private String environment;

    /**
     * Percentage rollout (0-100)
     */
    private Integer rolloutPercentage;

    /**
     * Optional scheduled activation date/time.
     * If current time is before this value,
     * the flag is considered inactive.
     */
    private LocalDateTime startDate;

    /**
     * Optional scheduled expiration date/time.
     * If current time is after this value,
     * the flag is considered inactive.
     */
    private LocalDateTime endDate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "flag_target_users",
            joinColumns = @JoinColumn(name = "flag_id")
    )
    @Column(name = "user_id")
    @Builder.Default
    private List<String> targetUsers = new ArrayList<>();
}