package com.featureflag.notification_service.scheduler;

import com.featureflag.notification_service.service.NotificationDeliveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationDeliverySchedulerTest {

    @Mock
    private NotificationDeliveryService deliveryService;

    @Test
    void scheduledInvocationDelegatesToDeliveryService() {
        NotificationDeliveryScheduler scheduler =
                new NotificationDeliveryScheduler(
                        deliveryService
                );

        scheduler.processDueNotifications();

        verify(deliveryService).processDueNotifications();
    }
}
