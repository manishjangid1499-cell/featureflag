package com.featureflag.analytics_service.kafka;

import com.featureflag.analytics_service.entity.AnalyticsEvent;
import com.featureflag.analytics_service.event.FlagEvent;
import com.featureflag.analytics_service.repository.AnalyticsEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalyticsEventConsumer {

    private final AnalyticsEventRepository analyticsEventRepository;

    @KafkaListener(
            topics = "feature-flag-events",
            groupId = "analytics-group"
    )
    public void consume(FlagEvent event) {

        AnalyticsEvent analyticsEvent =
                analyticsEventRepository
                        .findByFlagKeyAndEventType(
                                event.getFlagKey(),
                                event.getEventType()
                        )
                        .orElse(
                                AnalyticsEvent.builder()
                                        .flagKey(event.getFlagKey())
                                        .eventType(event.getEventType())
                                        .count(0L)
                                        .build()
                        );

        analyticsEvent.setCount(
                analyticsEvent.getCount() + 1
        );

        analyticsEventRepository.save(
                analyticsEvent
        );

        System.out.println(
                "Analytics Updated: "
                        + event.getFlagKey()
                        + " -> "
                        + analyticsEvent.getCount()
        );
    }
}