package com.featureflag.analytics_service.service;

import com.featureflag.analytics_service.entity.AnalyticsEvent;
import com.featureflag.analytics_service.repository.AnalyticsEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    public Page<AnalyticsEvent> getAllAnalytics(Pageable pageable) {
        return analyticsEventRepository.findAll(pageable);
    }

    /**
     * Get analytics for a specific flag.
     */
    public List<AnalyticsEvent> getAnalyticsByFlagKey(
            String flagKey
    ) {

        return analyticsEventRepository.findByFlagKey(flagKey);
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
    @Transactional
    public AnalyticsEvent processEvent(
            String flagKey,
            String environment,
            String eventType
    ) {
        analyticsEventRepository.incrementOrCreate(
                flagKey,
                environment,
                eventType
        );

        return analyticsEventRepository
                .findByFlagKeyAndEnvironmentAndEventType(
                        flagKey,
                        environment,
                        eventType
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Atomic analytics update did not produce a row"
                ));
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
