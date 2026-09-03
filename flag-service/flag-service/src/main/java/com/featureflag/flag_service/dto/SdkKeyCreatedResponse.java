package com.featureflag.flag_service.dto;

import java.time.Instant;

public record SdkKeyCreatedResponse(
        Long id,
        String name,
        String environment,
        String keyPrefix,
        boolean active,
        Instant createdAt,
        String createdBy,
        String rawKey
) {
}
