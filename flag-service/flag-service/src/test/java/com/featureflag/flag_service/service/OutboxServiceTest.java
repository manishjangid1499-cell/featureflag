package com.featureflag.flag_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.entity.OutboxEvent;
import com.featureflag.flag_service.event.FlagAuditSnapshot;
import com.featureflag.flag_service.repository.FeatureFlagRepository;
import com.featureflag.flag_service.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxServiceTest {

    private final OutboxEventRepository repository =
            mock(OutboxEventRepository.class);

    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    private final FeatureFlagRepository featureFlagRepository =
            mock(FeatureFlagRepository.class);

    private final FlagAuditContext flagAuditContext =
            new FlagAuditContext();

    private final OutboxService outboxService =
            new OutboxService(
                    repository,
                    objectMapper,
                    featureFlagRepository,
                    flagAuditContext
            );

    @Test
    void flagEventIsStoredAsPendingWithEventId()
            throws Exception {

        String eventId =
                outboxService.enqueueFlagEvent(
                        "FLAG_UPDATED",
                        "checkout",
                        "DEV"
                );

        ArgumentCaptor<OutboxEvent> captor =
                ArgumentCaptor.forClass(
                        OutboxEvent.class
                );

        verify(repository).save(captor.capture());

        OutboxEvent stored = captor.getValue();

        assertThat(stored.getId())
                .isEqualTo(eventId);
        assertThat(stored.getTopic())
                .isEqualTo("feature-flag-events");
        assertThat(stored.getMessageKey())
                .isEqualTo("checkout");
        assertThat(stored.getStatus())
                .isEqualTo(
                        OutboxEvent.STATUS_PENDING
                );
        assertThat(stored.getAttempts())
                .isZero();
        assertThat(stored.getCreatedAt())
                .isNotNull();
        assertThat(stored.getNextAttemptAt())
                .isNotNull();

        JsonNode payload =
                objectMapper.readTree(
                        stored.getPayload()
                );

        assertThat(
                payload.get("eventId").asText()
        ).isEqualTo(eventId);
        assertThat(
                payload.get("eventType").asText()
        ).isEqualTo("FLAG_UPDATED");
        assertThat(
                payload.get("flagKey").asText()
        ).isEqualTo("checkout");
        assertThat(
                payload.get("environment").asText()
        ).isEqualTo("DEV");
        assertThat(payload.get("sourceService").asText())
                .isEqualTo("flag-service");
        assertThat(payload.get("occurredAt").asText())
                .isNotBlank();
        assertThat(payload.get("actor").isNull()).isTrue();
        assertThat(payload.get("before").isNull()).isTrue();
        assertThat(payload.get("after").isNull()).isTrue();
    }

    @Test
    void createEventContainsActorAndCreatedSnapshot()
            throws Exception {
        FeatureFlag created = flag(
                10L,
                "checkout",
                true,
                "new description"
        );
        when(featureFlagRepository.findByFlagKeyAndEnvironment(
                "checkout",
                "DEV"
        )).thenReturn(Optional.of(created));

        JsonNode payload = enrichedPayload(
                "FLAG_CREATED",
                null
        );

        assertCommonEnrichment(payload);
        assertThat(payload.get("before").isNull()).isTrue();
        assertThat(payload.at("/after/id").asLong()).isEqualTo(10L);
        assertThat(payload.at("/after/enabled").asBoolean()).isTrue();
        assertThat(payload.at("/after/targetUsers/0").asText())
                .isEqualTo("user-1");
    }

    @Test
    void updateEventContainsDistinctBeforeAndAfterSnapshots()
            throws Exception {
        FeatureFlag before = flag(
                10L,
                "checkout",
                false,
                "old description"
        );
        FeatureFlag after = flag(
                10L,
                "checkout",
                true,
                "new description"
        );
        when(featureFlagRepository.findByFlagKeyAndEnvironment(
                "checkout",
                "DEV"
        )).thenReturn(Optional.of(after));

        JsonNode payload = enrichedPayload(
                "FLAG_UPDATED",
                FlagAuditSnapshot.from(before)
        );

        assertCommonEnrichment(payload);
        assertThat(payload.at("/before/description").asText())
                .isEqualTo("old description");
        assertThat(payload.at("/after/description").asText())
                .isEqualTo("new description");
    }

    @Test
    void toggleEventCapturesEnabledTransition()
            throws Exception {
        FeatureFlag before = flag(
                10L,
                "checkout",
                false,
                "description"
        );
        FeatureFlag after = flag(
                10L,
                "checkout",
                true,
                "description"
        );
        when(featureFlagRepository.findByFlagKeyAndEnvironment(
                "checkout",
                "DEV"
        )).thenReturn(Optional.of(after));

        JsonNode payload = enrichedPayload(
                "FLAG_TOGGLED",
                FlagAuditSnapshot.from(before)
        );

        assertThat(payload.at("/before/enabled").asBoolean()).isFalse();
        assertThat(payload.at("/after/enabled").asBoolean()).isTrue();
    }

    @Test
    void deleteEventPreservesDeletedSnapshotWithoutAfterState()
            throws Exception {
        FeatureFlag deleted = flag(
                10L,
                "checkout",
                true,
                "deleted description"
        );

        JsonNode payload = enrichedPayload(
                "FLAG_DELETED",
                FlagAuditSnapshot.from(deleted)
        );

        assertCommonEnrichment(payload);
        assertThat(payload.at("/before/id").asLong()).isEqualTo(10L);
        assertThat(payload.at("/before/description").asText())
                .isEqualTo("deleted description");
        assertThat(payload.get("after").isNull()).isTrue();
        verify(featureFlagRepository, never())
                .findByFlagKeyAndEnvironment(
                        "checkout",
                        "DEV"
                );
    }

    @Test
    void notificationEventIsStoredAsPendingWithEventId()
            throws Exception {

        String eventId =
                outboxService
                        .enqueueNotificationEvent(
                                "Flag changed",
                                "Checkout changed"
                        );

        ArgumentCaptor<OutboxEvent> captor =
                ArgumentCaptor.forClass(
                        OutboxEvent.class
                );

        verify(repository).save(captor.capture());

        OutboxEvent stored = captor.getValue();

        assertThat(stored.getId())
                .isEqualTo(eventId);
        assertThat(stored.getTopic())
                .isEqualTo("notification-events");
        assertThat(stored.getMessageKey())
                .isEqualTo(eventId);

        JsonNode payload =
                objectMapper.readTree(
                        stored.getPayload()
                );

        assertThat(
                payload.get("eventId").asText()
        ).isEqualTo(eventId);
        assertThat(
                payload.get("subject").asText()
        ).isEqualTo("Flag changed");
        assertThat(
                payload.get("type").asText()
        ).isEqualTo("EMAIL");
    }

    private JsonNode enrichedPayload(
            String eventType,
            FlagAuditSnapshot before
    ) throws Exception {
        flagAuditContext.within(
                new FlagAuditContext.AuditDetails(
                        "actor-123",
                        before
                ),
                () -> outboxService.enqueueFlagEvent(
                        eventType,
                        "checkout",
                        "DEV"
                )
        );

        ArgumentCaptor<OutboxEvent> captor =
                ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        return objectMapper.readTree(captor.getValue().getPayload());
    }

    private void assertCommonEnrichment(JsonNode payload) {
        assertThat(payload.get("eventId").asText()).isNotBlank();
        assertThat(payload.get("sourceService").asText())
                .isEqualTo("flag-service");
        assertThat(payload.get("actor").asText())
                .isEqualTo("actor-123");
        assertThat(payload.get("occurredAt").asText()).isNotBlank();
        assertThat(payload.get("timestamp").asText()).isNotBlank();
        assertThat(payload.toString())
                .doesNotContainIgnoringCase(
                        "password",
                        "authorization",
                        "privateKey",
                        "secret",
                        "token"
                );
    }

    private FeatureFlag flag(
            Long id,
            String flagKey,
            boolean enabled,
            String description
    ) {
        return FeatureFlag.builder()
                .id(id)
                .flagKey(flagKey)
                .name("Checkout")
                .description(description)
                .environment("DEV")
                .enabled(enabled)
                .rolloutPercentage(50)
                .startDate(LocalDateTime.parse(
                        "2026-08-27T10:00:00"
                ))
                .endDate(LocalDateTime.parse(
                        "2026-08-28T10:00:00"
                ))
                .targetUsers(List.of("user-1", "user-2"))
                .build();
    }
}
