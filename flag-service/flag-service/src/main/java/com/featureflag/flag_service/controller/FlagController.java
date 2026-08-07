package com.featureflag.flag_service.controller;

import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import com.featureflag.flag_service.dto.FlagRequest;
import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.service.FlagService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/flags")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class FlagController {

    private final FlagService flagService;

    @PostMapping
    public FeatureFlag createFlag(
            @RequestBody FlagRequest request) {

        return flagService.createFlag(request);
    }

    @GetMapping
    public List<FeatureFlag> getAllFlags() {

        return flagService.getAllFlags();
    }

    @GetMapping("/{key}")
    public FeatureFlag getFlagByKey(
            @PathVariable String key) {

        return flagService.getByKey(key);
    }

    @GetMapping("/{flagKey}/evaluate")
    public FlagEvaluationResponse evaluateFlag(
            @PathVariable String flagKey,
            @RequestParam String userId,
            @RequestParam String environment) {

        return flagService.evaluateFlag(
                flagKey,
                userId,
                environment
        );
    }

    @PutMapping("/{id}")
    public FeatureFlag updateFlag(
            @PathVariable Long id,
            @RequestBody FlagRequest request) {

        return flagService.updateFlag(id, request);
    }

    @DeleteMapping("/{id}")
    public String deleteFlag(
            @PathVariable Long id) {

        return flagService.deleteFlag(id);
    }

    @PatchMapping("/{id}/toggle")
    public FeatureFlag toggleFlag(
            @PathVariable Long id) {

        return flagService.toggleFlag(id);
    }
}