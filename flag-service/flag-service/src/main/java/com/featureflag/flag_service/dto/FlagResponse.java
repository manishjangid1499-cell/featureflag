package com.featureflag.flag_service.dto;

import com.featureflag.flag_service.entity.FeatureFlag;

import java.time.LocalDateTime;
import java.util.List;

public record FlagResponse(
        Long id,
        String flagKey,
        String name,
        String description,
        String environment,
        Boolean enabled,
        Integer rolloutPercentage,
        LocalDateTime startDate,
        LocalDateTime endDate,
        List<String> targetUsers
) {
    public static FlagResponse from(FeatureFlag flag) {
        return new FlagResponse(
                flag.getId(),
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
