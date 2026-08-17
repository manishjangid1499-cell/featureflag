package com.featureflag.analytics_service.service;

import com.featureflag.analytics_service.entity.AnalyticsEvent;
import com.featureflag.analytics_service.repository.AnalyticsEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
    @DisplayName("Process Event - New Event Initializes Count to 1")
    void testProcessEvent_NewEvent() {
        when(repository.findByFlagKeyAndEventType("DARK_MODE", "FLAG_CREATED")).thenReturn(Optional.empty());
        when(repository.save(any(AnalyticsEvent.class))).thenAnswer(i -> i.getArgument(0));

        AnalyticsEvent created = service.processEvent("DARK_MODE", "FLAG_CREATED");

        assertNotNull(created);
        assertEquals(1L, created.getCount());
        assertEquals("DARK_MODE", created.getFlagKey());
        assertEquals("FLAG_CREATED", created.getEventType());
    }

    @Test
    @DisplayName("Process Event - Existing Event Increments Count from 5 to 6")
    void testProcessEvent_ExistingEvent() {
        when(repository.findByFlagKeyAndEventType("NEW_CHECKOUT", "FLAG_EVALUATED")).thenReturn(Optional.of(testEvent));
        when(repository.save(any(AnalyticsEvent.class))).thenAnswer(i -> i.getArgument(0));

        AnalyticsEvent updated = service.processEvent("NEW_CHECKOUT", "FLAG_EVALUATED");

        assertNotNull(updated);
        assertEquals(6L, updated.getCount());
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
