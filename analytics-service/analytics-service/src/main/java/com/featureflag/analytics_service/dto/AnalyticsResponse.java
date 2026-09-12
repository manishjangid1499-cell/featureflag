package com.featureflag.analytics_service.dto;

import com.featureflag.analytics_service.entity.AnalyticsEvent;

public record AnalyticsResponse(
        Long id,
        String flagKey,
        String environment,
        String eventType,
        Long count
) {
    public static AnalyticsResponse from(AnalyticsEvent event) {
        return new AnalyticsResponse(
                event.getId(),
                event.getFlagKey(),
                event.getEnvironment(),
                event.getEventType(),
                event.getCount()
        );
    }
}
