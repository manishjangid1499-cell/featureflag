package com.featureflag.notification_service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.notification_service.dto.NotificationRequest;
import com.featureflag.notification_service.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationKafkaConsumerTest {

    @Test
    void directKafkaNotificationPreservesEventCreatorIdentity() {
        NotificationService notificationService = mock(NotificationService.class);
        NotificationKafkaConsumer consumer = new NotificationKafkaConsumer(
                notificationService,
                new ObjectMapper()
        );

        consumer.consumeNotificationEvent("""
                {
                  "recipient": "recipient@company.com",
                  "creatorEmail": "event-creator@company.com",
                  "subject": "Flag changed",
                  "message": "A flag changed",
                  "type": "EMAIL"
                }
                """);

        ArgumentCaptor<NotificationRequest> requestCaptor =
                ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).createNotification(requestCaptor.capture());
        assertEquals(
                "event-creator@company.com",
                requestCaptor.getValue().getCreatorEmail()
        );
    }
}
