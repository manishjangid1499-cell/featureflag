package com.featureflag.analytics_service.kafka;

import com.featureflag.analytics_service.entity.AnalyticsEvent;
import com.featureflag.analytics_service.event.FlagEvent;
import com.featureflag.analytics_service.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsEventConsumer {

    private final AnalyticsService analyticsService;

    @KafkaListener(
            topics = "feature-flag-events",
            groupId = "analytics-group"
    )
    public void consume(FlagEvent event) {

        try {

            log.info(
                    "Received feature flag event: {} - {}",
                    event.getEventType(),
                    event.getFlagKey()
            );

            AnalyticsEvent analyticsEvent =
                    analyticsService.processEvent(
                            event.getFlagKey(),
                            event.getEventType()
                    );

            log.info(
                    "Analytics Updated: {} -> {}",
                    event.getFlagKey(),
                    analyticsEvent.getCount()
            );

        } catch (Exception e) {

            log.error(
                    "Failed to process feature flag event",
                    e
            );
        }
    }
}