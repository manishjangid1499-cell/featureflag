package com.featureflag.auth_service.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateMemberStatusRequest(
        @NotNull(message = "enabled is required")
        Boolean enabled
) {
}
