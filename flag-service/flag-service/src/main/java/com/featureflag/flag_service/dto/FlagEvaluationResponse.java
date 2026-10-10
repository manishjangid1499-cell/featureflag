package com.featureflag.flag_service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

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
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant startDate;

    /**
     * Scheduled expiration time.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant endDate;

    /**
     * Indicates whether the current time
     * falls within the configured schedule.
     */
    private boolean withinSchedule;
}