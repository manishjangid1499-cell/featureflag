package com.featureflag.flag_service.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlagRequest {

    public static final String KEY_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]{0,254}";

    @Min(value = 0, message = "expectedVersion cannot be negative")
    private Long expectedVersion;

    @NotBlank(message = "name is required")
    @Size(max = 255, message = "name cannot exceed 255 characters")
    private String name;

    @NotBlank(message = "flagKey is required")
    @Size(max = 255, message = "flagKey cannot exceed 255 characters")
    @Pattern(
            regexp = KEY_PATTERN,
            message = "flagKey may contain only letters, numbers, dots, underscores, and hyphens"
    )
    private String flagKey;

    @NotNull(message = "enabled is required")
    private Boolean enabled;

    @Size(max = 255, message = "description cannot exceed 255 characters")
    private String description;

    @NotBlank(message = "environment is required")
    @Size(max = 20, message = "environment cannot exceed 20 characters")
    @Pattern(
            regexp = "(?i:DEV|QA|STAGING|PROD)",
            message = "environment must be DEV, QA, STAGING, or PROD"
    )
    private String environment;

    @Min(value = 0, message = "rolloutPercentage cannot be less than 0")
    @Max(value = 100, message = "rolloutPercentage cannot be greater than 100")
    private Integer rolloutPercentage;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private List<
            @NotBlank(message = "target user cannot be blank")
            @Size(max = 255, message = "target user cannot exceed 255 characters")
            String> targetUsers;

    @AssertTrue(message = "endDate cannot be before startDate")
    public boolean isScheduleValid() {
        return startDate == null
                || endDate == null
                || !endDate.isBefore(startDate);
    }
}
