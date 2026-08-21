package com.featureflag.analytics_service.service;

import com.featureflag.analytics_service.entity.AnalyticsEvent;
import com.featureflag.analytics_service.repository.AnalyticsEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AnalyticsEventRepository analyticsEventRepository;

    /**
     * Get all analytics records.
     */
    public List<AnalyticsEvent> getAllAnalytics() {

        return analyticsEventRepository.findAll();
    }

    /**
     * Get analytics for a specific flag.
     */
    public List<AnalyticsEvent> getAnalyticsByFlagKey(
            String flagKey
    ) {

        return analyticsEventRepository.findByFlagKey(flagKey);
    }

    /**
     * Get analytics by ID.
     */
    public AnalyticsEvent getAnalyticsById(Long id) {

        return analyticsEventRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Analytics record not found with id: "
                                        + id
                        )
                );
    }

    /**
     * Process an event received from Kafka.
     */
    public AnalyticsEvent processEvent(
            String flagKey,
            String environment,
            String eventType
    ) {
        AnalyticsEvent analyticsEvent =
                analyticsEventRepository
                        .findByFlagKeyAndEnvironmentAndEventType(
                                flagKey,
                                environment,
                                eventType
                        )
                        .orElse(
                                AnalyticsEvent.builder()
                                        .flagKey(flagKey)
                                        .environment(environment)
                                        .eventType(eventType)
                                        .count(0L)
                                        .build()
                        );
        analyticsEvent.setCount(
                analyticsEvent.getCount() + 1
        );
        return analyticsEventRepository.save(
                analyticsEvent
        );
    }

    /**
     * Delete analytics record.
     */
    public void deleteAnalytics(Long id) {

        if (!analyticsEventRepository.existsById(id)) {

            throw new RuntimeException(
                    "Analytics record not found with id: "
                            + id
            );
        }

        analyticsEventRepository.deleteById(id);
    }
}