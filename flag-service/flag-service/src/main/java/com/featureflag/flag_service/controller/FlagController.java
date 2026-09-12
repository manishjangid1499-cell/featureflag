package com.featureflag.flag_service.controller;

import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import com.featureflag.flag_service.dto.FlagRequest;
import com.featureflag.flag_service.dto.FlagResponse;
import com.featureflag.flag_service.dto.PageResponse;
import com.featureflag.flag_service.service.FlagEvaluationTelemetryService;
import com.featureflag.flag_service.service.FlagMutationAuditService;
import com.featureflag.flag_service.service.FlagQueryService;
import com.featureflag.flag_service.service.FlagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/flags")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(
        name = "Feature Flags",
        description = "APIs for creating, reading, updating, deleting, toggling and evaluating feature flags"
)
public class FlagController {

    private final FlagService flagService;
    private final FlagEvaluationTelemetryService
            flagEvaluationTelemetryService;
    private final FlagMutationAuditService
            flagMutationAuditService;
    private final FlagQueryService flagQueryService;

    @Operation(
            summary = "Create a feature flag",
            description = "Creates a new feature flag and publishes a FLAG_CREATED Kafka event"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Feature flag created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping
    public ResponseEntity<FlagResponse> createFlag(
            @Valid @RequestBody FlagRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                FlagResponse.from(
                        flagMutationAuditService.createFlag(
                                request,
                                authentication.getName()
                        )
                )
        );
    }

    @Operation(summary = "Get all feature flags", description = "Returns all feature flags")
    @ApiResponse(responseCode = "200", description = "Feature flags retrieved successfully")
    @GetMapping
    public ResponseEntity<PageResponse<FlagResponse>> getAllFlags(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page cannot be negative")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size cannot exceed 100")
            int size
    ) {
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "id")
        );
        return ResponseEntity.ok(PageResponse.from(
                flagQueryService.findAll(pageable),
                FlagResponse::from
        ));
    }

    @Operation(summary = "Get feature flag by database ID", description = "Returns a feature flag using its numerical ID")
    @GetMapping("/id/{id}")
    public ResponseEntity<FlagResponse> getFlagById(@PathVariable Long id) {
        return ResponseEntity.ok(
                FlagResponse.from(flagService.getById(id))
        );
    }

    @Operation(
            summary = "Get feature flag by key and environment",
            description = "Returns a feature flag using its flag key and environment"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Flag found"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid or unsupported environment"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Flag not found"
            )
    })
    @GetMapping("/{key}")
    public ResponseEntity<FlagResponse> getFlagByKey(

            @Parameter(
                    description = "Feature flag key",
                    example = "NEW_CHECKOUT"
            )
            @PathVariable
            @Pattern(regexp = FlagRequest.KEY_PATTERN, message = "Invalid flag key format or length")
            String key,

            @Parameter(
                    description = "Environment",
                    example = "DEV"
            )
            @RequestParam String environment
    ) {

        return ResponseEntity.ok(FlagResponse.from(
                flagService.getByKey(key, environment)
        ));
    }

    @Operation(
            summary = "Evaluate a feature flag",
            description = "Evaluates whether a feature flag should be enabled for a user given schedule, targeting whitelist, and rollout bucket"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Flag evaluated successfully"),
            @ApiResponse(responseCode = "404", description = "Flag not found")
    })
    @GetMapping("/{flagKey}/evaluate")
    public ResponseEntity<FlagEvaluationResponse> evaluateFlag(
            @Parameter(description = "Feature flag key", example = "NEW_CHECKOUT")
            @PathVariable
            @Pattern(regexp = FlagRequest.KEY_PATTERN, message = "Invalid flag key format or length")
            String flagKey,
            @Parameter(description = "User ID used for targeting and rollout calculation", example = "user123")
            @RequestParam String userId,
            @Parameter(description = "Environment", example = "DEV")
            @RequestParam String environment) {

        FlagEvaluationResponse response =
                flagEvaluationTelemetryService.evaluateFlag(
                        flagKey,
                        userId,
                        environment
                );
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update a feature flag", description = "Updates a feature flag and publishes a FLAG_UPDATED Kafka event")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feature flag updated successfully"),
            @ApiResponse(responseCode = "404", description = "Flag not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PutMapping("/{id}")
    public ResponseEntity<FlagResponse> updateFlag(
            @Parameter(description = "Feature flag ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody FlagRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(FlagResponse.from(
                flagMutationAuditService.updateFlag(
                        id,
                        request,
                        authentication.getName()
                )
        ));
    }

    @Operation(summary = "Delete a feature flag", description = "Deletes a feature flag and publishes a FLAG_DELETED Kafka event")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feature flag deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Flag not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteFlag(
            @Parameter(description = "Feature flag ID", example = "1")
            @PathVariable Long id,
            Authentication authentication) {

        String message = flagMutationAuditService.deleteFlag(
                id,
                authentication.getName()
        );
        return ResponseEntity.ok(message);
    }

    @Operation(summary = "Toggle feature flag", description = "Inverts the enabled state of a feature flag and publishes a FLAG_TOGGLED Kafka event")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feature flag toggled successfully"),
            @ApiResponse(responseCode = "404", description = "Flag not found")
    })
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<FlagResponse> toggleFlag(
            @Parameter(description = "Feature flag ID", example = "1")
            @PathVariable Long id,
            Authentication authentication) {

        return ResponseEntity.ok(FlagResponse.from(
                flagMutationAuditService.toggleFlag(
                        id,
                        authentication.getName()
                )
        ));
    }
}
