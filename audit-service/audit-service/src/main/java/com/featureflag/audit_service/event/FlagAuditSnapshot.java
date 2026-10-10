package com.featureflag.audit_service.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.featureflag.audit_service.config.LenientInstantDeserializer;

import java.time.Instant;
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
        @JsonDeserialize(using = LenientInstantDeserializer.class)
        Instant startDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @JsonDeserialize(using = LenientInstantDeserializer.class)
        Instant endDate,
        List<String> targetUsers
) {

    public FlagAuditSnapshot {
        targetUsers = targetUsers == null
                ? null
                : Collections.unmodifiableList(
                        new ArrayList<>(targetUsers)
                );
    }
}
