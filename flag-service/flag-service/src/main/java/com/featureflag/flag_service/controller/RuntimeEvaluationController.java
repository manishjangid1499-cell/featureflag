package com.featureflag.flag_service.controller;

import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import com.featureflag.flag_service.dto.RuntimeEvaluationResponse;
import com.featureflag.flag_service.security.SdkKeyPrincipal;
import com.featureflag.flag_service.service.FlagEvaluationTelemetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/runtime/v1/flags")
@RequiredArgsConstructor
public class RuntimeEvaluationController {

    private final FlagEvaluationTelemetryService evaluationService;

    @GetMapping("/{flagKey}/evaluate")
    public ResponseEntity<RuntimeEvaluationResponse> evaluate(
            @PathVariable String flagKey,
            @RequestParam String subject,
            @AuthenticationPrincipal SdkKeyPrincipal principal
    ) {
        FlagEvaluationResponse evaluation =
                evaluationService.evaluateFlag(
                        flagKey,
                        subject,
                        principal.environment()
                );

        return ResponseEntity.ok(new RuntimeEvaluationResponse(
                evaluation.getFlagKey(),
                evaluation.getEnvironment(),
                evaluation.isEnabled()
        ));
    }
}
