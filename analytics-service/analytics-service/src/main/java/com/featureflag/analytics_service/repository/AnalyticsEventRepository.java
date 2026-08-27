package com.featureflag.analytics_service.repository;

import com.featureflag.analytics_service.entity.AnalyticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query(
            value = """
                    INSERT INTO analytics_events (
                        count,
                        environment,
                        event_type,
                        flag_key
                    ) VALUES (
                        1,
                        :environment,
                        :eventType,
                        :flagKey
                    )
                    ON DUPLICATE KEY UPDATE
                        count = count + 1
                    """,
            nativeQuery = true
    )
    int incrementOrCreate(
            @Param("flagKey") String flagKey,
            @Param("environment") String environment,
            @Param("eventType") String eventType
    );

    Optional<AnalyticsEvent> findByFlagKeyAndEnvironmentAndEventType(
            String flagKey,
            String environment,
            String eventType
    );

    List<AnalyticsEvent> findByFlagKey(
            String flagKey
    );
}
