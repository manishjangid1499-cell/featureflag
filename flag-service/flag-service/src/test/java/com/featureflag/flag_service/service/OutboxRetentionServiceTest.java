package com.featureflag.flag_service.service;

import com.featureflag.flag_service.entity.OutboxEvent;
import com.featureflag.flag_service.repository.OutboxEventRepository;
import com.featureflag.flag_service.observability.FlagMetrics;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRetentionServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-09-01T12:00:00Z");
    private static final Clock CLOCK =
            Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void cleanupUsesPublishedStatusAgeAndConfiguredBound() {
        OutboxEventRepository repository =
                mock(OutboxEventRepository.class);
        OutboxRetentionService service =
                new OutboxRetentionService(
                        repository,
                        CLOCK,
                        Duration.ofDays(7),
                        25,
                        mock(FlagMetrics.class)
                );
        List<String> ids = List.of("event-1", "event-2");
        when(repository.findPublishedIdsBefore(
                OutboxEvent.STATUS_PUBLISHED,
                NOW.minus(Duration.ofDays(7)),
                org.springframework.data.domain.PageRequest.of(0, 25)
        )).thenReturn(ids);
        when(repository.deletePublishedByIdIn(
                OutboxEvent.STATUS_PUBLISHED,
                NOW.minus(Duration.ofDays(7)),
                ids
        )).thenReturn(2);

        assertThat(service.deleteExpiredPublishedEvents())
                .isEqualTo(2);
        verify(repository).findPublishedIdsBefore(
                OutboxEvent.STATUS_PUBLISHED,
                NOW.minus(Duration.ofDays(7)),
                org.springframework.data.domain.PageRequest.of(0, 25)
        );
        verify(repository).deletePublishedByIdIn(
                OutboxEvent.STATUS_PUBLISHED,
                NOW.minus(Duration.ofDays(7)),
                ids
        );
    }

    @Test
    void invalidRetentionConfigurationFailsFast() {
        OutboxEventRepository repository =
                mock(OutboxEventRepository.class);
        FlagMetrics metrics = mock(FlagMetrics.class);

        assertThatThrownBy(() -> new OutboxRetentionService(
                repository,
                CLOCK,
                Duration.ZERO,
                25,
                metrics
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutboxRetentionService(
                repository,
                CLOCK,
                Duration.ofDays(7),
                1_001,
                metrics
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
