package com.featureflag.flag_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.flag_service.entity.OutboxEvent;
import com.featureflag.flag_service.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxServiceTest {

    private final OutboxEventRepository repository =
            mock(OutboxEventRepository.class);

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    private final OutboxService outboxService =
            new OutboxService(
                    repository,
                    objectMapper
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
}
