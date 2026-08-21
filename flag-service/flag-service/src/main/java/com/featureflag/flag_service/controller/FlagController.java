package com.featureflag.flag_service.controller;

import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import com.featureflag.flag_service.dto.FlagRequest;
import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.service.FlagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/flags")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(
        name = "Feature Flags",
        description = "APIs for creating, reading, updating, deleting, toggling and evaluating feature flags"
)
public class FlagController {

    private final FlagService flagService;

    // =========================================================
    // CREATE FLAG
    // =========================================================

    @Operation(
            summary = "Create a feature flag",
            description = "Creates a new feature flag and publishes a FLAG_CREATED Kafka event"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Feature flag created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping
    public ResponseEntity<FeatureFlag> createFlag(@Valid @RequestBody FlagRequest request) {
        FeatureFlag createdFlag = flagService.createFlag(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdFlag);
    }

    // =========================================================
    // GET ALL FLAGS
    // =========================================================

    @Operation(summary = "Get all feature flags", description = "Returns all feature flags")
    @ApiResponse(responseCode = "200", description = "Feature flags retrieved successfully")
    @GetMapping
    public ResponseEntity<List<FeatureFlag>> getAllFlags() {
        List<FeatureFlag> flags = flagService.getAllFlags();
        return ResponseEntity.ok(flags);
    }

    // =========================================================
    // GET FLAG BY ID
    // =========================================================

    @Operation(summary = "Get feature flag by database ID", description = "Returns a feature flag using its numerical ID")
    @GetMapping("/id/{id}")
    public ResponseEntity<FeatureFlag> getFlagById(@PathVariable Long id) {
        FeatureFlag flag = flagService.getById(id);
        return ResponseEntity.ok(flag);
    }

    // =========================================================
    // GET FLAG BY KEY
    // =========================================================

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
    public ResponseEntity<FeatureFlag> getFlagByKey(

            @Parameter(
                    description = "Feature flag key",
                    example = "NEW_CHECKOUT"
            )
            @PathVariable String key,

            @Parameter(
                    description = "Environment",
                    example = "DEV"
            )
            @RequestParam String environment
    ) {

        FeatureFlag flag =
                flagService.getByKey(
                        key,
                        environment
                );

        return ResponseEntity.ok(flag);
    }

    // =========================================================
    // EVALUATE FLAG
    // =========================================================

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
            @PathVariable String flagKey,
            @Parameter(description = "User ID used for targeting and rollout calculation", example = "user123")
            @RequestParam String userId,
            @Parameter(description = "Environment", example = "DEV")
            @RequestParam String environment) {

        FlagEvaluationResponse response = flagService.evaluateFlag(flagKey, userId, environment);
        return ResponseEntity.ok(response);
    }

    // =========================================================
    // UPDATE FLAG
    // =========================================================

    @Operation(summary = "Update a feature flag", description = "Updates a feature flag and publishes a FLAG_UPDATED Kafka event")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feature flag updated successfully"),
            @ApiResponse(responseCode = "404", description = "Flag not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PutMapping("/{id}")
    public ResponseEntity<FeatureFlag> updateFlag(
            @Parameter(description = "Feature flag ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody FlagRequest request) {

        FeatureFlag updatedFlag = flagService.updateFlag(id, request);
        return ResponseEntity.ok(updatedFlag);
    }

    // =========================================================
    // DELETE FLAG
    // =========================================================

    @Operation(summary = "Delete a feature flag", description = "Deletes a feature flag and publishes a FLAG_DELETED Kafka event")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feature flag deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Flag not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteFlag(
            @Parameter(description = "Feature flag ID", example = "1")
            @PathVariable Long id) {

        String message = flagService.deleteFlag(id);
        return ResponseEntity.ok(message);
    }

    // =========================================================
    // TOGGLE FLAG
    // =========================================================

    @Operation(summary = "Toggle feature flag", description = "Inverts the enabled state of a feature flag and publishes a FLAG_TOGGLED Kafka event")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feature flag toggled successfully"),
            @ApiResponse(responseCode = "404", description = "Flag not found")
    })
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<FeatureFlag> toggleFlag(
            @Parameter(description = "Feature flag ID", example = "1")
            @PathVariable Long id) {

        FeatureFlag toggledFlag = flagService.toggleFlag(id);
        return ResponseEntity.ok(toggledFlag);
    }
}