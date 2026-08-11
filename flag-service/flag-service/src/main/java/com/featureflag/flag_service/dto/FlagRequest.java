package com.featureflag.flag_service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlagRequest {

    @NotBlank(message = "name is required")
    private String name;

    @NotBlank(message = "flagKey is required")
    private String flagKey;

    @NotNull(message = "enabled is required")
    private Boolean enabled;

    private String description;

    @NotBlank(message = "environment is required")
    private String environment;

    @Min(value = 0, message = "rolloutPercentage cannot be less than 0")
    @Max(value = 100, message = "rolloutPercentage cannot be greater than 100")
    private Integer rolloutPercentage;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private List<String> targetUsers;
}