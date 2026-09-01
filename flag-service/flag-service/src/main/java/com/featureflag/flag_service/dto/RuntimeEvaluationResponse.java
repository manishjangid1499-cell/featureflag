package com.featureflag.flag_service.dto;

public record RuntimeEvaluationResponse(
        String flagKey,
        String environment,
        boolean enabled
) {
}
