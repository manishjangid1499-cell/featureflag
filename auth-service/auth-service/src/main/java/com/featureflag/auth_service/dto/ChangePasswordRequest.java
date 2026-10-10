package com.featureflag.auth_service.dto;

import com.featureflag.auth_service.validation.BcryptPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "New password must be at least 8 characters")
        @BcryptPassword
        String newPassword,

        String confirmPassword
) {
}
