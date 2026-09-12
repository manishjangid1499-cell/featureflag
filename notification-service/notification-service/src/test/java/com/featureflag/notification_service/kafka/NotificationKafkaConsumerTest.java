package com.featureflag.notification_service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.notification_service.dto.NotificationEvent;
import com.featureflag.notification_service.entity.ProcessedEvent;
import com.featureflag.notification_service.exception.UnsupportedNotificationChannelException;
import com.featureflag.notification_service.repository.ProcessedEventRepository;
import com.featureflag.notification_service.observability.NotificationMetrics;
import com.featureflag.notification_service.service.NotificationIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaConsumerTest {

    @Mock
    private NotificationIngestionService ingestionService;

    @Mock
    private ProcessedEventRepository processedRepository;

    @Mock
    private NotificationMetrics notificationMetrics;

    private NotificationKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationKafkaConsumer(
                ingestionService,
                new ObjectMapper(),
                processedRepository,
                notificationMetrics
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
        verify(processedRepository, never()).save(
                any(ProcessedEvent.class)
        );
        verify(notificationMetrics).eventIngested();
    }

    @Test
    void missingKafkaTypeDefaultsToEmailBeforeIngestion()
            throws Exception {
        consumer.consumeNotificationEvent(
                directEventJsonWithoutType()
        );

        ArgumentCaptor<NotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(NotificationEvent.class);
        verify(ingestionService).ingestDirectNotificationEvent(
                eq("event-null-type-1"),
                eventCaptor.capture()
        );
        assertEquals("EMAIL", eventCaptor.getValue().getType());
    }

    @Test
    void explicitNullKafkaTypeDefaultsToEmailBeforeIngestion()
            throws Exception {
        consumer.consumeNotificationEvent(
                directEventJsonWithNullType()
        );

        ArgumentCaptor<NotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(NotificationEvent.class);
        verify(ingestionService).ingestDirectNotificationEvent(
                eq("event-explicit-null-type-1"),
                eventCaptor.capture()
        );
        assertEquals("EMAIL", eventCaptor.getValue().getType());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SMS",
            "PUSH",
            "FAX",
            "email",
            "Email",
            "",
            "   "
    })
    void unsupportedKafkaTypeIsRejectedBeforeRoleLookupOrIngestion(
            String type
    ) {
        assertThatThrownBy(
                () -> consumer.consumeNotificationEvent(
                        roleEventJsonWithType(type)
                )
        )
                .isInstanceOf(
                        UnsupportedNotificationChannelException.class
                )
                .hasMessage(
                        "Only EMAIL notification channel is supported"
                );

        verify(processedRepository)
                .existsById("event-unsupported-type-1");
        verifyNoInteractions(
                ingestionService
        );
        verify(processedRepository, never()).save(
                any(ProcessedEvent.class)
        );
    }

    @Test
    void roleEventUsesRecipientsFromTheNotificationCommand()
            throws Exception {
        List<String> recipients = List.of(
                "owner@company.com",
                "admin@company.com"
        );

        consumer.consumeNotificationEvent(roleEventJson());

        verify(ingestionService)
                .ingestRoleNotificationEvent(
                        eq("event-role-1"),
                        any(NotificationEvent.class),
                        eq(recipients)
                );
        verify(processedRepository)
                .existsById("event-role-1");
        verify(processedRepository, never()).save(
                any(ProcessedEvent.class)
        );
        verify(notificationMetrics).eventIngested();
    }

    @Test
    void duplicateEventIsSkippedBeforeResolutionOrIngestion()
            throws Exception {
        when(processedRepository.existsById("event-role-1"))
                .thenReturn(true);

        consumer.consumeNotificationEvent(roleEventJson());

        verifyNoInteractions(
                ingestionService
        );
        verify(processedRepository, never()).save(
                any(ProcessedEvent.class)
        );
        verify(notificationMetrics).duplicateIgnored();
    }

    @Test
    void emptyRoleRecipientsAreDurablyIngestedAsNoOp()
            throws Exception {
        consumer.consumeNotificationEvent(emptyRoleEventJson());

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

        verify(processedRepository, never()).save(
                any(ProcessedEvent.class)
        );
    }

    @Test
    void roleEventWithoutEnrichedRecipientsIsRejected() {
        assertThatThrownBy(
                () -> consumer.consumeNotificationEvent(
                        roleEventJsonWithoutRecipients()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Kafka notification recipients are required");

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
                  "type": "EMAIL",
                  "recipients": [
                    " OWNER@company.com ",
                    "admin@company.com",
                    "owner@company.com"
                  ]
                }
                """;
    }

    private String roleEventJsonWithoutRecipients() {
        return """
                {
                  "eventId": "event-role-missing-recipients",
                  "subject": "Flag changed",
                  "message": "A flag changed",
                  "type": "EMAIL"
                }
                """;
    }

    private String emptyRoleEventJson() {
        return """
                {
                  "eventId": "event-role-1",
                  "subject": "Flag changed",
                  "message": "A flag changed",
                  "type": "EMAIL",
                  "recipients": []
                }
                """;
    }

    private String directEventJsonWithoutType() {
        return """
                {
                  "eventId": "event-null-type-1",
                  "recipient": "recipient@company.com",
                  "creatorEmail": "event-creator@company.com",
                  "subject": "Flag changed",
                  "message": "A flag changed"
                }
                """;
    }

    private String roleEventJsonWithType(String type) {
        return """
                {
                  "eventId": "event-unsupported-type-1",
                  "subject": "Flag changed",
                  "message": "A flag changed",
                  "type": "%s"
                }
                """.formatted(type);
    }

    private String directEventJsonWithNullType() {
        return """
                {
                  "eventId": "event-explicit-null-type-1",
                  "recipient": "recipient@company.com",
                  "creatorEmail": "event-creator@company.com",
                  "subject": "Flag changed",
                  "message": "A flag changed",
                  "type": null
                }
                """;
    }
}
