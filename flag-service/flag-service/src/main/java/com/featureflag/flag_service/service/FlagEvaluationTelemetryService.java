package com.featureflag.flag_service.service;

import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlagEvaluationTelemetryService {

    private final FlagService flagService;
    private final EvaluationTelemetryPublisher telemetryPublisher;

    public FlagEvaluationResponse evaluateFlag(
            String flagKey,
            String userId,
            String environment
    ) {
        FlagEvaluationResponse evaluation =
                flagService.evaluateFlag(
                        flagKey,
                        userId,
                        environment
                );

        try {
            telemetryPublisher.publish(evaluation);
        } catch (RuntimeException exception) {
            log.warn(
                    "Evaluation telemetry dispatch failed; "
                            + "flagKey={} environment={} errorType={}",
                    evaluation.getFlagKey(),
                    evaluation.getEnvironment(),
                    exception.getClass().getSimpleName()
            );
        }

        return evaluation;
    }
}
