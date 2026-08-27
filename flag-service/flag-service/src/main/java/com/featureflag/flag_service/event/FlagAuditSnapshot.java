package com.featureflag.flag_service.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.featureflag.flag_service.entity.FeatureFlag;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record FlagAuditSnapshot(
        Long id,
        String flagKey,
        String name,
        String description,
        String environment,
        Boolean enabled,
        Integer rolloutPercentage,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        LocalDateTime startDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        LocalDateTime endDate,
        List<String> targetUsers
) {

    public FlagAuditSnapshot {
        targetUsers = targetUsers == null
                ? null
                : Collections.unmodifiableList(
                        new ArrayList<>(targetUsers)
                );
    }

    public static FlagAuditSnapshot from(FeatureFlag flag) {
        return new FlagAuditSnapshot(
                flag.getId(),
                flag.getFlagKey(),
                flag.getName(),
                flag.getDescription(),
                flag.getEnvironment(),
                flag.getEnabled(),
                flag.getRolloutPercentage(),
                flag.getStartDate(),
                flag.getEndDate(),
                flag.getTargetUsers()
        );
    }
}
