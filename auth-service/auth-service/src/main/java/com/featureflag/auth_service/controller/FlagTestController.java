package com.featureflag.auth_service.controller;

import com.featureflag.auth_service.client.FlagClient;
import com.featureflag.auth_service.dto.FeatureFlagResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class FlagTestController {

    private final FlagClient flagClient;

    @Operation(summary = "Get feature flag")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/flag/{key}")
    public FeatureFlagResponse getFlag(
            @PathVariable String key) {

        return flagClient.getFlagByKey(key);
    }
}