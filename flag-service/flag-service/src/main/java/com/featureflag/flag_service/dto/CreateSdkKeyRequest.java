package com.featureflag.flag_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSdkKeyRequest(
        @NotBlank
        @Size(max = 120)
        String name,
        @NotBlank
        @Size(max = 20)
        String environment
) {
}
