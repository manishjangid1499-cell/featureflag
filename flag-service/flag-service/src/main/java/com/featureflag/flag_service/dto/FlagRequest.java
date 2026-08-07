package com.featureflag.flag_service.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class FlagRequest {

    private String name;

    private String flagKey;

    private Boolean enabled;

    private String description;

    /**
     * DEV, QA, STAGING, PROD
     */
    private String environment;

    private Integer rolloutPercentage;

    /**
     * Scheduled activation time.
     * Optional.
     */
    private LocalDateTime startDate;

    /**
     * Scheduled expiration time.
     * Optional.
     */
    private LocalDateTime endDate;

    private List<String> targetUsers;
}