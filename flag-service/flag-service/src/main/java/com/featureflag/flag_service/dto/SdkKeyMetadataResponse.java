package com.featureflag.flag_service.dto;

import java.time.Instant;

public record SdkKeyMetadataResponse(
        Long id,
        String name,
        String environment,
        String keyPrefix,
        boolean active,
        Instant createdAt,
        Instant revokedAt,
        String createdBy
) {
}
