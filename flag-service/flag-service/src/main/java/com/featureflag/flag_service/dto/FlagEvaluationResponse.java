package com.featureflag.flag_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class FlagEvaluationResponse {

    private String flagKey;

    private String environment;

    private boolean enabled;

    private boolean targetedUser;

    private Integer rolloutPercentage;

    /**
     * Scheduled activation time.
     */
    private LocalDateTime startDate;

    /**
     * Scheduled expiration time.
     */
    private LocalDateTime endDate;

    /**
     * Indicates whether the current time
     * falls within the configured schedule.
     */
    private boolean withinSchedule;
}