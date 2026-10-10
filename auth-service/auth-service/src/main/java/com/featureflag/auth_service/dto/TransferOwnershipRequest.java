package com.featureflag.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TransferOwnershipRequest(
        @NotNull(message = "New owner user ID is required")
        Long newOwnerId,

        @NotBlank(message = "Current password is required")
        String currentPassword
) {
}
