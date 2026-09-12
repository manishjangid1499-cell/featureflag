package com.featureflag.analytics_service.service;

import com.featureflag.analytics_service.entity.AnalyticsEvent;
import com.featureflag.analytics_service.exception.ResourceNotFoundException;
import com.featureflag.analytics_service.observability.AnalyticsMetrics;
import com.featureflag.analytics_service.repository.AnalyticsEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AnalyticsEventRepository analyticsEventRepository;
    private final AnalyticsMetrics analyticsMetrics;

    public Page<AnalyticsEvent> getAllAnalytics(Pageable pageable) {
        return analyticsEventRepository.findAll(pageable);
    }

    public Page<AnalyticsEvent> getAnalyticsByFlagKey(
            String flagKey,
            Pageable pageable
    ) {
        return analyticsEventRepository.findByFlagKey(
                flagKey,
                pageable
        );
    }

    public AnalyticsEvent getAnalyticsById(Long id) {

        return analyticsEventRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Analytics record not found with id: "
                                        + id
                        )
                );
    }

    @Transactional
    public AnalyticsEvent processEvent(
            String flagKey,
            String environment,
            String eventType
    ) {
        try {
            analyticsEventRepository.incrementOrCreate(
                    flagKey,
                    environment,
                    eventType
            );

            AnalyticsEvent result = analyticsEventRepository
                    .findByFlagKeyAndEnvironmentAndEventType(
                            flagKey,
                            environment,
                            eventType
                    )
                    .orElseThrow(() -> new IllegalStateException(
                            "Atomic analytics update did not produce a row"
                    ));
            analyticsMetrics.ingestionSucceeded(eventType);
            return result;
        } catch (RuntimeException exception) {
            analyticsMetrics.aggregationFailed(eventType);
            throw exception;
        }
    }

    public void deleteAnalytics(Long id) {

        AnalyticsEvent event = analyticsEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Analytics record not found with id: " + id
                ));
        analyticsEventRepository.delete(event);
    }
}
