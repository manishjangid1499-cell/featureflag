package com.featureflag.flag_service.dto;

import java.time.LocalDateTime;

public record SdkKeyMetadataResponse(
        Long id,
        String name,
        String environment,
        String keyPrefix,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime revokedAt,
        String createdBy
) {
}
