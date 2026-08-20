package com.featureflag.notification_service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.notification_service.dto.NotificationRequest;
import com.featureflag.notification_service.entity.ProcessedEvent;
import com.featureflag.notification_service.repository.ProcessedEventRepository;
import com.featureflag.notification_service.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationKafkaConsumerTest {

    @Test
    void directKafkaNotificationPreservesCreatorAndStoresMarker()
            throws Exception {

        NotificationService notificationService =
                mock(NotificationService.class);

        ProcessedEventRepository processedRepository =
                mock(ProcessedEventRepository.class);

        NotificationKafkaConsumer consumer =
                consumer(
                        notificationService,
                        processedRepository
                );

        consumer.consumeNotificationEvent(
                directEventJson()
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

        verify(processedRepository).save(
                any(ProcessedEvent.class)
        );
    }

    @Test
    void duplicateEventIsSkipped()
            throws Exception {

        NotificationService notificationService =
                mock(NotificationService.class);

        ProcessedEventRepository processedRepository =
                mock(ProcessedEventRepository.class);

        when(
                processedRepository.existsById(
                        "event-1"
                )
        ).thenReturn(true);

        NotificationKafkaConsumer consumer =
                consumer(
                        notificationService,
                        processedRepository
                );

        consumer.consumeNotificationEvent(
                directEventJson()
        );

        verifyNoInteractions(notificationService);

        verify(
                processedRepository,
                never()
        ).save(any(ProcessedEvent.class));
    }

    @Test
    void malformedJsonPropagatesToKafkaContainer() {
        NotificationKafkaConsumer consumer =
                consumer(
                        mock(NotificationService.class),
                        mock(ProcessedEventRepository.class)
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
    void missingEventIdIsRejected() {
        NotificationService notificationService =
                mock(NotificationService.class);

        ProcessedEventRepository processedRepository =
                mock(ProcessedEventRepository.class);

        NotificationKafkaConsumer consumer =
                consumer(
                        notificationService,
                        processedRepository
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
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "Kafka eventId is required"
                );

        verifyNoInteractions(notificationService);
    }

    @Test
    void notificationServiceFailurePropagatesWithoutMarker() {
        NotificationService notificationService =
                mock(NotificationService.class);

        ProcessedEventRepository processedRepository =
                mock(ProcessedEventRepository.class);

        doThrow(
                new RuntimeException(
                        "database unavailable"
                )
        ).when(notificationService)
                .createNotification(
                        any(NotificationRequest.class)
                );

        NotificationKafkaConsumer consumer =
                consumer(
                        notificationService,
                        processedRepository
                );

        assertThatThrownBy(
                () -> consumer.consumeNotificationEvent(
                        directEventJson()
                )
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessage("database unavailable");

        verify(
                processedRepository,
                never()
        ).save(any(ProcessedEvent.class));
    }

    private NotificationKafkaConsumer consumer(
            NotificationService notificationService,
            ProcessedEventRepository processedRepository
    ) {
        return new NotificationKafkaConsumer(
                notificationService,
                new ObjectMapper(),
                processedRepository
        );
    }

    private String directEventJson() {
        return """
                {
                  "eventId": "event-1",
                  "recipient": "recipient@company.com",
                  "creatorEmail": "event-creator@company.com",
                  "subject": "Flag changed",
                  "message": "A flag changed",
                  "type": "EMAIL"
                }
                """;
    }
}
