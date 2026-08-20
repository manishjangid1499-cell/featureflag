package com.featureflag.audit_service.kafka;

import com.featureflag.audit_service.entity.AuditLog;
import com.featureflag.audit_service.event.FlagEvent;
import com.featureflag.audit_service.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditEventConsumerTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditEventConsumer consumer;

    @Test
    void successfulEventIsPersisted() {
        FlagEvent event = event();

        consumer.consume(event);

        ArgumentCaptor<AuditLog> captor =
                ArgumentCaptor.forClass(AuditLog.class);

        verify(auditLogRepository).save(
                captor.capture()
        );

        assertThat(captor.getValue().getEventType())
                .isEqualTo("UPDATED");
        assertThat(captor.getValue().getFlagKey())
                .isEqualTo("checkout");
    }

    @Test
    void persistenceFailurePropagatesToKafkaContainer() {
        when(
                auditLogRepository.save(
                        any(AuditLog.class)
                )
        ).thenThrow(
                new RuntimeException("database unavailable")
        );

        assertThatThrownBy(
                () -> consumer.consume(event())
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessage("database unavailable");
    }

    private FlagEvent event() {
        FlagEvent event = new FlagEvent();
        event.setEventType("UPDATED");
        event.setFlagKey("checkout");
        event.setTimestamp("2026-08-20T10:00:00Z");
        return event;
    }
}
