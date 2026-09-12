package com.featureflag.flag_service.service;

import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import com.featureflag.flag_service.observability.FlagMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlagEvaluationTelemetryService {

    private final FlagService flagService;
    private final EvaluationTelemetryPublisher telemetryPublisher;
    private final FlagMetrics flagMetrics;

    public FlagEvaluationResponse evaluateFlag(
            String flagKey,
            String userId,
            String environment
    ) {
        Timer.Sample sample = flagMetrics.startEvaluation();
        FlagEvaluationResponse evaluation;
        try {
            evaluation = flagService.evaluateFlag(
                    flagKey,
                    userId,
                    environment
            );
        } catch (RuntimeException exception) {
            flagMetrics.evaluationFailed(sample);
            throw exception;
        }

        try {
            telemetryPublisher.publish(evaluation);
        } catch (RuntimeException exception) {
            flagMetrics.telemetryPublished(false);
            log.warn(
                    "Evaluation telemetry dispatch failed; "
                            + "flagKey={} environment={} errorType={}",
                    evaluation.getFlagKey(),
                    evaluation.getEnvironment(),
                    exception.getClass().getSimpleName()
            );
        }

        // Include bounded admission in the decision-path timer, not background Kafka work.
        flagMetrics.evaluationCompleted(sample, evaluation.isEnabled());

        return evaluation;
    }
}
