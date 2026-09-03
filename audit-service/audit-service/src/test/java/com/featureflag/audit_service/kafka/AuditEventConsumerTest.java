package com.featureflag.audit_service.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.audit_service.entity.AuditLog;
import com.featureflag.audit_service.entity.ProcessedEvent;
import com.featureflag.audit_service.event.FlagAuditSnapshot;
import com.featureflag.audit_service.event.FlagEvent;
import com.featureflag.audit_service.repository.AuditLogRepository;
import com.featureflag.audit_service.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditEventConsumerTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ProcessedEventRepository
            processedEventRepository;

    private AuditEventConsumer consumer;

    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        consumer = new AuditEventConsumer(
                auditLogRepository,
                processedEventRepository,
                objectMapper
        );
    }

    @Test
    void enrichedEventPersistsSnapshotsActorSourceAndMarker()
            throws Exception {
        FlagEvent event = event();
        event.setSourceService("flag-service");
        event.setActor("actor-123");
        event.setBefore(snapshot(false, "old description"));
        event.setAfter(snapshot(true, "new description"));
        event.setOccurredAt(LocalDateTime.parse(
                "2026-08-20T10:00:00.123456"
        ));

        consumer.consume(event);

        ArgumentCaptor<AuditLog> auditCaptor =
                ArgumentCaptor.forClass(AuditLog.class);

        verify(auditLogRepository).save(
                auditCaptor.capture()
        );

        AuditLog auditLog = auditCaptor.getValue();
        assertThat(auditLog.getEventId()).isEqualTo("event-1");
        assertThat(auditLog.getEventType())
                .isEqualTo("FLAG_UPDATED");
        assertThat(auditLog.getFlagKey())
                .isEqualTo("checkout");
        assertThat(auditLog.getEnvironment())
                .isEqualTo("DEV");
        assertThat(auditLog.getSourceService())
                .isEqualTo("flag-service");
        assertThat(auditLog.getActor()).isEqualTo("actor-123");
        assertThat(auditLog.getOccurredAt()).isEqualTo(
                Instant.parse("2026-08-20T10:00:00.123456Z")
        );

        JsonNode before = objectMapper.readTree(
                auditLog.getBeforeState()
        );
        JsonNode after = objectMapper.readTree(
                auditLog.getAfterState()
        );
        assertThat(before.get("enabled").asBoolean()).isFalse();
        assertThat(before.get("description").asText())
                .isEqualTo("old description");
        assertThat(after.get("enabled").asBoolean()).isTrue();
        assertThat(after.get("description").asText())
                .isEqualTo("new description");

        ArgumentCaptor<ProcessedEvent> markerCaptor =
                ArgumentCaptor.forClass(
                        ProcessedEvent.class
                );

        verify(processedEventRepository).save(
                markerCaptor.capture()
        );

        assertThat(markerCaptor.getValue().getEventId())
                .isEqualTo("event-1");
        assertThat(markerCaptor.getValue().getTopic())
                .isEqualTo("feature-flag-events");
    }

    @Test
    void legacyEventWithoutOptionalEnrichmentRemainsConsumable() {
        consumer.consume(event());

        ArgumentCaptor<AuditLog> captor =
                ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog auditLog = captor.getValue();
        assertThat(auditLog.getSourceService()).isNull();
        assertThat(auditLog.getActor()).isNull();
        assertThat(auditLog.getBeforeState()).isNull();
        assertThat(auditLog.getAfterState()).isNull();
        assertThat(auditLog.getOccurredAt()).isEqualTo(
                Instant.parse("2026-08-20T10:00:00Z")
        );
    }

    @Test
    void unparseableLegacyTimestampIsPreservedWithoutInventingTime() {
        FlagEvent event = event();
        event.setTimestamp("legacy-time-value");

        consumer.consume(event);

        ArgumentCaptor<AuditLog> captor =
                ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getTimestamp())
                .isEqualTo("legacy-time-value");
        assertThat(captor.getValue().getOccurredAt()).isNull();
    }

    @Test
    void evaluationTelemetryIsRejectedBeforeAuditWrites() {
        FlagEvent event = event();
        event.setEventType("EVALUATION_ENABLED");
        assertRejectedBeforeWrites(event);
    }

    @Test
    void duplicateEventIsSkipped() {
        when(
                processedEventRepository.existsById(
                        "event-1"
                )
        ).thenReturn(true);

        consumer.consume(event());

        verify(
                auditLogRepository,
                never()
        ).save(any(AuditLog.class));

        verify(
                processedEventRepository,
                never()
        ).save(any(ProcessedEvent.class));
    }

    @Test
    void persistenceFailurePropagatesWithoutMarker() {
        when(
                auditLogRepository.save(
                        any(AuditLog.class)
                )
        ).thenThrow(
                new RuntimeException(
                        "database unavailable"
                )
        );

        assertThatThrownBy(
                () -> consumer.consume(event())
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessage("database unavailable");

        verify(
                processedEventRepository,
                never()
        ).save(any(ProcessedEvent.class));
    }

    @Test
    void missingEventIdIsRejected() {
        FlagEvent event = event();
        event.setEventId(null);

        assertThatThrownBy(
                () -> consumer.consume(event)
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "Kafka eventId is required"
                );

        verify(
                auditLogRepository,
                never()
        ).save(any(AuditLog.class));
    }

    @Test
    void missingEventTypeIsRejectedBeforeWrites() {
        FlagEvent event = event();
        event.setEventType(null);
        assertRejectedBeforeWrites(event);
    }

    @Test
    void blankFlagKeyIsRejectedBeforeWrites() {
        FlagEvent event = event();
        event.setFlagKey(" ");
        assertRejectedBeforeWrites(event);
    }

    @Test
    void missingEnvironmentIsRejectedBeforeWrites() {
        FlagEvent event = event();
        event.setEnvironment(null);
        assertRejectedBeforeWrites(event);
    }

    @Test
    void blankTimestampIsRejectedBeforeWrites() {
        FlagEvent event = event();
        event.setTimestamp(" ");
        assertRejectedBeforeWrites(event);
    }

    private void assertRejectedBeforeWrites(
            FlagEvent event
    ) {
        assertThatThrownBy(
                () -> consumer.consume(event)
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                );
        verify(
                auditLogRepository,
                never()
        ).save(any(AuditLog.class));
        verify(
                processedEventRepository,
                never()
        ).save(any(ProcessedEvent.class));
    }

    private FlagEvent event() {
        FlagEvent event = new FlagEvent();
        event.setEventId("event-1");
        event.setEventType("FLAG_UPDATED");
        event.setFlagKey("checkout");
        event.setEnvironment("DEV");
        event.setTimestamp(
                "2026-08-20T10:00:00Z"
        );
        return event;
    }

    private FlagAuditSnapshot snapshot(
            boolean enabled,
            String description
    ) {
        return new FlagAuditSnapshot(
                10L,
                "checkout",
                "Checkout",
                description,
                "DEV",
                enabled,
                50,
                null,
                null,
                List.of("user-1")
        );
    }
}
