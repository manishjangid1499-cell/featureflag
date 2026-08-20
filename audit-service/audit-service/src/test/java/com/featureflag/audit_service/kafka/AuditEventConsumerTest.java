package com.featureflag.audit_service.kafka;

import com.featureflag.audit_service.entity.AuditLog;
import com.featureflag.audit_service.entity.ProcessedEvent;
import com.featureflag.audit_service.event.FlagEvent;
import com.featureflag.audit_service.repository.AuditLogRepository;
import com.featureflag.audit_service.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private AuditEventConsumer consumer;

    @Test
    void successfulEventPersistsBusinessRecordAndMarker() {
        FlagEvent event = event();

        consumer.consume(event);

        ArgumentCaptor<AuditLog> auditCaptor =
                ArgumentCaptor.forClass(AuditLog.class);

        verify(auditLogRepository).save(
                auditCaptor.capture()
        );

        assertThat(auditCaptor.getValue().getEventType())
                .isEqualTo("UPDATED");
        assertThat(auditCaptor.getValue().getFlagKey())
                .isEqualTo("checkout");

        ArgumentCaptor<ProcessedEvent> markerCaptor =
                ArgumentCaptor.forClass(
                        ProcessedEvent.class
                );

        verify(processedEventRepository).save(
                markerCaptor.capture()
        );

        assertThat(markerCaptor.getValue().getEventId())
                .isEqualTo("event-1");
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

    private FlagEvent event() {
        FlagEvent event = new FlagEvent();
        event.setEventId("event-1");
        event.setEventType("UPDATED");
        event.setFlagKey("checkout");
        event.setTimestamp(
                "2026-08-20T10:00:00Z"
        );
        return event;
    }
}
