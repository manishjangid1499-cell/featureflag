package com.featureflag.notification_service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.notification_service.dto.NotificationEvent;
import com.featureflag.notification_service.dto.NotificationRequest;
import com.featureflag.notification_service.entity.ProcessedEvent;
import com.featureflag.notification_service.repository.ProcessedEventRepository;
import com.featureflag.notification_service.service.NotificationIngestionService;
import com.featureflag.notification_service.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationIngestionService ingestionService;

    @Mock
    private ProcessedEventRepository processedRepository;

    private NotificationKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationKafkaConsumer(
                notificationService,
                ingestionService,
                new ObjectMapper(),
                processedRepository
        );
    }

    @Test
    void directEventUsesDurableIngestionWithoutSynchronousDelivery()
            throws Exception {
        consumer.consumeNotificationEvent(directEventJson());

        ArgumentCaptor<NotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(NotificationEvent.class);

        verify(processedRepository).existsById("event-1");
        verify(ingestionService).ingestDirectNotificationEvent(
                eq("event-1"),
                eventCaptor.capture()
        );
        assertEquals(
                "recipient@company.com",
                eventCaptor.getValue().getRecipient()
        );
        assertEquals(
                "event-creator@company.com",
                eventCaptor.getValue().getCreatorEmail()
        );
        verifyNoInteractions(notificationService);
        verify(processedRepository, never()).save(
                any(ProcessedEvent.class)
        );
    }

    @Test
    void roleEventResolvesRecipientsBeforeDurableIngestion()
            throws Exception {
        List<String> recipients = List.of(
                "owner@company.com",
                "admin@company.com"
        );
        when(
                notificationService.resolveRoleRecipientEmails(
                        List.of("OWNER", "ADMIN")
                )
        ).thenReturn(recipients);

        consumer.consumeNotificationEvent(roleEventJson());

        InOrder order = inOrder(
                notificationService,
                ingestionService
        );
        order.verify(notificationService)
                .resolveRoleRecipientEmails(
                        List.of("OWNER", "ADMIN")
                );
        order.verify(ingestionService)
                .ingestRoleNotificationEvent(
                        eq("event-role-1"),
                        any(NotificationEvent.class),
                        eq(recipients)
                );
        verify(processedRepository)
                .existsById("event-role-1");
        verify(notificationService, never())
                .createNotification(
                        any(NotificationRequest.class)
                );
        verify(notificationService, never())
                .sendToRoleRecipients(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyList()
                );
        verify(processedRepository, never()).save(
                any(ProcessedEvent.class)
        );
    }

    @Test
    void duplicateEventIsSkippedBeforeResolutionOrIngestion()
            throws Exception {
        when(processedRepository.existsById("event-role-1"))
                .thenReturn(true);

        consumer.consumeNotificationEvent(roleEventJson());

        verifyNoInteractions(
                notificationService,
                ingestionService
        );
        verify(processedRepository, never()).save(
                any(ProcessedEvent.class)
        );
    }

    @Test
    void emptyRoleRecipientsAreDurablyIngestedAsNoOp()
            throws Exception {
        when(
                notificationService.resolveRoleRecipientEmails(
                        List.of("OWNER", "ADMIN")
                )
        ).thenReturn(List.of());

        consumer.consumeNotificationEvent(roleEventJson());

        verify(ingestionService)
                .ingestRoleNotificationEvent(
                        eq("event-role-1"),
                        any(NotificationEvent.class),
                        eq(List.of())
                );
    }

    @Test
    void malformedJsonPropagatesToKafkaContainer() {
        assertThatThrownBy(
                () -> consumer.consumeNotificationEvent(
                        "{not-json"
                )
        ).isInstanceOf(
                com.fasterxml.jackson.core
                        .JsonProcessingException.class
        );

        verifyNoInteractions(
                notificationService,
                ingestionService,
                processedRepository
        );
    }

    @Test
    void missingEventIdIsRejected() {
        assertInvalidEventId(
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
    }

    @Test
    void blankEventIdIsRejected() {
        assertInvalidEventId(
                """
                {
                  "eventId": "   ",
                  "recipient": "recipient@company.com",
                  "subject": "Flag changed",
                  "message": "A flag changed",
                  "type": "EMAIL"
                }
                """
        );
    }

    @Test
    void ingestionFailurePropagatesToKafkaContainer() {
        RuntimeException ingestionFailure =
                new RuntimeException("database unavailable");
        doThrow(ingestionFailure)
                .when(ingestionService)
                .ingestDirectNotificationEvent(
                        eq("event-1"),
                        any(NotificationEvent.class)
                );

        assertThatThrownBy(
                () -> consumer.consumeNotificationEvent(
                        directEventJson()
                )
        ).isSameAs(ingestionFailure);

        verifyNoInteractions(notificationService);
        verify(processedRepository, never()).save(
                any(ProcessedEvent.class)
        );
    }

    @Test
    void roleRecipientLookupFailurePropagatesWithoutIngestion() {
        IllegalStateException lookupFailure =
                new IllegalStateException(
                        "Failed to retrieve notification recipients from Auth Service"
                );
        when(
                notificationService.resolveRoleRecipientEmails(
                        List.of("OWNER", "ADMIN")
                )
        ).thenThrow(lookupFailure);

        assertThatThrownBy(
                () -> consumer.consumeNotificationEvent(
                        roleEventJson()
                )
        ).isSameAs(lookupFailure);

        verifyNoInteractions(ingestionService);
        verify(processedRepository, never()).save(
                any(ProcessedEvent.class)
        );
    }

    private void assertInvalidEventId(String eventJson) {
        assertThatThrownBy(
                () -> consumer.consumeNotificationEvent(
                        eventJson
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Kafka eventId is required");

        verifyNoInteractions(
                notificationService,
                ingestionService,
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

    private String roleEventJson() {
        return """
                {
                  "eventId": "event-role-1",
                  "subject": "Flag changed",
                  "message": "A flag changed",
                  "type": "EMAIL"
                }
                """;
    }
}
