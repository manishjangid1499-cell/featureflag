package com.featureflag.audit_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.audit_service.entity.AuditLog;
import com.featureflag.audit_service.event.FlagEvent;
import com.featureflag.audit_service.kafka.AuditEventConsumer;
import com.featureflag.audit_service.repository.AuditLogRepository;
import com.featureflag.audit_service.repository.ProcessedEventRepository;
import com.featureflag.audit_service.observability.AuditMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    private static final PageRequest PAGE = PageRequest.of(1, 20);

    @Mock
    private AuditLogRepository repository;

    @Mock
    private ProcessedEventRepository
            processedEventRepository;

    @InjectMocks
    private AuditService auditService;

    private AuditLog testLog;

    @BeforeEach
    void setUp() {
        testLog = AuditLog.builder()
                .id(1L)
                .flagKey("NEW_CHECKOUT")
                .eventType("FLAG_CREATED")
                .timestamp(
                        LocalDateTime.now().toString()
                )
                .build();
    }

    @Test
    @DisplayName(
            "Get All Audit Logs - Returns records "
                    + "ordered by latest"
    )
    void testGetAllAuditLogs() {
        when(repository.findAllByOrderByIdDesc(PAGE))
                .thenReturn(new PageImpl<>(List.of(testLog), PAGE, 21));

        List<AuditLog> results =
                auditService.getAllAuditLogs(PAGE).getContent();

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(
                "NEW_CHECKOUT",
                results.get(0).getFlagKey()
        );
        assertEquals(
                "FLAG_CREATED",
                results.get(0).getEventType()
        );
    }

    @Test
    @DisplayName(
            "Get Audit Logs By Flag Key - "
                    + "Returns records for key"
    )
    void testGetAuditLogsByFlagKey() {
        when(
                repository
                        .findByFlagKeyOrderByOccurredAtDescIdDesc(
                                "NEW_CHECKOUT", PAGE
                        )
        ).thenReturn(new PageImpl<>(List.of(testLog), PAGE, 21));

        List<AuditLog> results =
                auditService
                        .getAuditLogsByFlagKey(
                                "NEW_CHECKOUT", PAGE
                        ).getContent();

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(
                "NEW_CHECKOUT",
                results.get(0).getFlagKey()
        );
    }

    @Test
    @DisplayName(
            "Get Audit Log By ID - Success"
    )
    void testGetAuditLogById_Success() {
        when(repository.findById(1L))
                .thenReturn(Optional.of(testLog));

        AuditLog found =
                auditService.getAuditLogById(1L);

        assertNotNull(found);
        assertEquals(1L, found.getId());
    }

    @Test
    @DisplayName(
            "Get Audit Log By ID - "
                    + "Not Found Throws RuntimeException"
    )
    void testGetAuditLogById_NotFound() {
        when(repository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                RuntimeException.class,
                () -> auditService
                        .getAuditLogById(999L)
        );
    }

    @Test
    @DisplayName(
            "Consume Event - Persists AuditLog "
                    + "in repository"
    )
    void testConsumeEvent() {
        AuditEventConsumer consumer =
                new AuditEventConsumer(
                        repository,
                        processedEventRepository,
                        new ObjectMapper().findAndRegisterModules(),
                        mock(AuditMetrics.class)
                );

        FlagEvent event =
                new FlagEvent(
                        "event-1",
                        "FLAG_UPDATED",
                        "DARK_MODE",
                        "DEV",
                        LocalDateTime.now().toString()
                );

        consumer.consume(event);

        verify(
                repository,
                times(1)
        ).save(any(AuditLog.class));
    }
}
