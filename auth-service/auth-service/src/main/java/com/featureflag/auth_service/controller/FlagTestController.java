package com.featureflag.auth_service.controller;

import com.featureflag.auth_service.client.FlagClient;
import com.featureflag.auth_service.dto.FeatureFlagResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class FlagTestController {

    private final FlagClient flagClient;

    @GetMapping("/flag/{key}")
    public FeatureFlagResponse getFlag(
            @PathVariable String key) {

        return flagClient.getFlagByKey(key);
    }
}