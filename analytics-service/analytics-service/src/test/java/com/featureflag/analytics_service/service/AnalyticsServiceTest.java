package com.featureflag.analytics_service.service;

import com.featureflag.analytics_service.entity.AnalyticsEvent;
import com.featureflag.analytics_service.repository.AnalyticsEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private AnalyticsEventRepository repository;

    @InjectMocks
    private AnalyticsService service;

    private AnalyticsEvent testEvent;

    @BeforeEach
    void setUp() {
        testEvent = AnalyticsEvent.builder()
                .id(1L)
                .flagKey("NEW_CHECKOUT")
                .environment("DEV")
                .eventType("FLAG_EVALUATED")
                .count(5L)
                .build();
    }

    @Test
    @DisplayName("Get All Analytics - Returns list of records")
    void testGetAllAnalytics() {
        when(repository.findAll()).thenReturn(List.of(testEvent));

        List<AnalyticsEvent> results = service.getAllAnalytics();

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("NEW_CHECKOUT", results.get(0).getFlagKey());
    }

    @Test
    @DisplayName("Get Analytics By Flag Key - Returns matching events")
    void testGetAnalyticsByFlagKey() {
        when(repository.findByFlagKey("NEW_CHECKOUT")).thenReturn(List.of(testEvent));

        List<AnalyticsEvent> results = service.getAnalyticsByFlagKey("NEW_CHECKOUT");

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(5L, results.get(0).getCount());
    }

    @Test
    @DisplayName("Get Analytics By ID - Success")
    void testGetAnalyticsById_Success() {
        when(repository.findById(1L)).thenReturn(Optional.of(testEvent));

        AnalyticsEvent found = service.getAnalyticsById(1L);

        assertNotNull(found);
        assertEquals(1L, found.getId());
    }

    @Test
    @DisplayName("Get Analytics By ID - Not Found Throws RuntimeException")
    void testGetAnalyticsById_NotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.getAnalyticsById(999L));
    }

    @Test
    @DisplayName("Process Event - Atomically Creates Count One")
    void testProcessEvent_NewEvent() {
        AnalyticsEvent created = AnalyticsEvent.builder()
                .id(2L)
                .flagKey("DARK_MODE")
                .environment("DEV")
                .eventType("FLAG_CREATED")
                .count(1L)
                .build();
        when(
                repository.findByFlagKeyAndEnvironmentAndEventType(
                        "DARK_MODE",
                        "DEV",
                        "FLAG_CREATED"
                )
        ).thenReturn(Optional.of(created));

        AnalyticsEvent result =
                service.processEvent(
                        "DARK_MODE",
                        "DEV",
                        "FLAG_CREATED"
                );

        assertSame(created, result);
        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository).incrementOrCreate(
                "DARK_MODE",
                "DEV",
                "FLAG_CREATED"
        );
        inOrder.verify(repository)
                .findByFlagKeyAndEnvironmentAndEventType(
                        "DARK_MODE",
                        "DEV",
                        "FLAG_CREATED"
                );
        verify(repository, never()).save(any(AnalyticsEvent.class));
    }

    @Test
    @DisplayName("Process Event - Atomically Increments Existing Aggregate")
    void testProcessEvent_ExistingEvent() {
        AnalyticsEvent updatedEvent = AnalyticsEvent.builder()
                .id(1L)
                .flagKey("NEW_CHECKOUT")
                .environment("DEV")
                .eventType("FLAG_EVALUATED")
                .count(6L)
                .build();
        when(
                repository.findByFlagKeyAndEnvironmentAndEventType(
                        "NEW_CHECKOUT",
                        "DEV",
                        "FLAG_EVALUATED"
                )
        ).thenReturn(Optional.of(updatedEvent));

        AnalyticsEvent updated =
                service.processEvent(
                        "NEW_CHECKOUT",
                        "DEV",
                        "FLAG_EVALUATED"
                );

        assertNotNull(updated);
        assertEquals(6L, updated.getCount());
        assertEquals("DEV", updated.getEnvironment());
        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository).incrementOrCreate(
                "NEW_CHECKOUT",
                "DEV",
                "FLAG_EVALUATED"
        );
        inOrder.verify(repository)
                .findByFlagKeyAndEnvironmentAndEventType(
                        "NEW_CHECKOUT",
                        "DEV",
                        "FLAG_EVALUATED"
                );
        verify(repository, never()).save(any(AnalyticsEvent.class));
    }

    @Test
    @DisplayName("Process Event - Same Flag Uses Separate Environment Aggregate")
    void testProcessEvent_SameFlagDifferentEnvironment() {
        AnalyticsEvent created = AnalyticsEvent.builder()
                .id(3L)
                .flagKey("NEW_CHECKOUT")
                .environment("PROD")
                .eventType("FLAG_EVALUATED")
                .count(1L)
                .build();
        when(
                repository.findByFlagKeyAndEnvironmentAndEventType(
                        "NEW_CHECKOUT",
                        "PROD",
                        "FLAG_EVALUATED"
                )
        ).thenReturn(Optional.of(created));
        AnalyticsEvent result =
                service.processEvent(
                        "NEW_CHECKOUT",
                        "PROD",
                        "FLAG_EVALUATED"
                );
        assertSame(created, result);
        verify(repository).incrementOrCreate(
                "NEW_CHECKOUT",
                "PROD",
                "FLAG_EVALUATED"
        );
        verify(repository, never()).save(any(AnalyticsEvent.class));
    }

    @Test
    @DisplayName("Delete Analytics - Success")
    void testDeleteAnalytics_Success() {
        when(repository.existsById(1L)).thenReturn(true);

        service.deleteAnalytics(1L);

        verify(repository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("Delete Analytics - Not Found Throws RuntimeException")
    void testDeleteAnalytics_NotFound() {
        when(repository.existsById(999L)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> service.deleteAnalytics(999L));
        verify(repository, never()).deleteById(anyLong());
    }
}
