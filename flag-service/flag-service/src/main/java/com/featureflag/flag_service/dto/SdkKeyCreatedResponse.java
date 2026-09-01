package com.featureflag.flag_service.dto;

import java.time.LocalDateTime;

public record SdkKeyCreatedResponse(
        Long id,
        String name,
        String environment,
        String keyPrefix,
        boolean active,
        LocalDateTime createdAt,
        String createdBy,
        String rawKey
) {
}
