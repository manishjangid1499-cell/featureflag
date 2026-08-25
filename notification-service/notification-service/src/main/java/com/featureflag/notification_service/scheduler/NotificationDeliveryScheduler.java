package com.featureflag.notification_service.scheduler;

import com.featureflag.notification_service.service.NotificationDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.notification.delivery",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class NotificationDeliveryScheduler {

    private final NotificationDeliveryService deliveryService;

    @Scheduled(
            fixedDelayString =
                    "${app.notification.delivery.poll-interval:5s}"
    )
    public void processDueNotifications() {
        deliveryService.processDueNotifications();
    }
}
