package com.featureflag.flag_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlagToggleRequest {

    @NotNull(message = "enabled is required")
    private Boolean enabled;

    @NotNull(message = "expectedVersion is required")
    @Min(value = 0, message = "expectedVersion cannot be negative")
    private Long expectedVersion;
}
