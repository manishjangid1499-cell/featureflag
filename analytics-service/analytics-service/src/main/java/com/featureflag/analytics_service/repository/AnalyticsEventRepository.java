package com.featureflag.analytics_service.repository;

import com.featureflag.analytics_service.entity.AnalyticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    Optional<AnalyticsEvent> findByFlagKeyAndEventType(
            String flagKey,
            String eventType
    );

    List<AnalyticsEvent> findByFlagKey(
            String flagKey
    );
}