package com.featureflag.notification_service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.notification_service.dto.NotificationRequest;
import com.featureflag.notification_service.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationKafkaConsumerTest {

    @Test
    void directKafkaNotificationPreservesEventCreatorIdentity()
            throws Exception {

        NotificationService notificationService =
                mock(NotificationService.class);

        NotificationKafkaConsumer consumer =
                new NotificationKafkaConsumer(
                        notificationService,
                        new ObjectMapper()
                );

        consumer.consumeNotificationEvent(
                """
                {
                  "recipient": "recipient@company.com",
                  "creatorEmail": "event-creator@company.com",
                  "subject": "Flag changed",
                  "message": "A flag changed",
                  "type": "EMAIL"
                }
                """
        );

        ArgumentCaptor<NotificationRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        NotificationRequest.class
                );

        verify(notificationService)
                .createNotification(
                        requestCaptor.capture()
                );

        assertEquals(
                "event-creator@company.com",
                requestCaptor.getValue()
                        .getCreatorEmail()
        );
    }

    @Test
    void malformedJsonPropagatesToKafkaContainer() {
        NotificationKafkaConsumer consumer =
                new NotificationKafkaConsumer(
                        mock(NotificationService.class),
                        new ObjectMapper()
                );

        assertThatThrownBy(
                () -> consumer.consumeNotificationEvent(
                        "{not-json"
                )
        ).isInstanceOf(
                com.fasterxml.jackson.core
                        .JsonProcessingException.class
        );
    }

    @Test
    void notificationServiceFailurePropagatesToKafkaContainer() {
        NotificationService notificationService =
                mock(NotificationService.class);

        doThrow(
                new RuntimeException(
                        "database unavailable"
                )
        ).when(notificationService)
                .createNotification(
                        any(NotificationRequest.class)
                );

        NotificationKafkaConsumer consumer =
                new NotificationKafkaConsumer(
                        notificationService,
                        new ObjectMapper()
                );

        assertThatThrownBy(
                () -> consumer.consumeNotificationEvent(
                        """
                        {
                          "recipient": "recipient@company.com",
                          "creatorEmail": "event-creator@company.com",
                          "subject": "Flag changed",
                          "message": "A flag changed",
                          "type": "EMAIL"
                        }
                        """
                )
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessage("database unavailable");
    }
}
