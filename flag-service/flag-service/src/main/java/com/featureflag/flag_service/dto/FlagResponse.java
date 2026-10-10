package com.featureflag.flag_service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.featureflag.flag_service.entity.FeatureFlag;

import java.time.Instant;
import java.util.List;

public record FlagResponse(
        Long id,
        Long version,
        String flagKey,
        String name,
        String description,
        String environment,
        Boolean enabled,
        Integer rolloutPercentage,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant startDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant endDate,
        List<String> targetUsers
) {
    public static FlagResponse from(FeatureFlag flag) {
        return new FlagResponse(
                flag.getId(),
                flag.getVersion(),
                flag.getFlagKey(),
                flag.getName(),
                flag.getDescription(),
                flag.getEnvironment(),
                flag.getEnabled(),
                flag.getRolloutPercentage(),
                flag.getStartDate(),
                flag.getEndDate(),
                flag.getTargetUsers() == null
                        ? List.of()
                        : List.copyOf(flag.getTargetUsers())
        );
    }
}
