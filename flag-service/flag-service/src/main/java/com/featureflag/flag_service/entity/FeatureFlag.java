package com.featureflag.flag_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "feature_flags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true)
    private String flagKey;

    private Boolean enabled;

    private String description;
}