package com.featureflag.flag_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.flag_service.client.AuthRecipientsClient;
import com.featureflag.flag_service.dto.NotificationEvent;
import com.featureflag.flag_service.entity.OutboxEvent;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPayloadEnricherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthRecipientsClient client =
            mock(AuthRecipientsClient.class);
    private final OutboxPayloadEnricher enricher =
            new OutboxPayloadEnricher(objectMapper, client);

    @Test
    void roleNotificationIsEnrichedWithNormalizedRecipients()
            throws Exception {
        OutboxEvent event = notificationEvent(null, null);
        when(client.getNotificationRecipients(
                List.of("OWNER", "ADMIN")
        )).thenReturn(Arrays.asList(
                " Owner@Company.com ",
                null,
                "",
                "owner@company.com",
                "admin@company.com"
        ));

        String payload = enricher.enrich(event);
        NotificationEvent result = objectMapper.readValue(
                payload,
                NotificationEvent.class
        );

        assertThat(result.getRecipients()).containsExactly(
                "owner@company.com",
                "admin@company.com"
        );
        assertThat(event.getPayload()).isEqualTo(payload);
    }

    @Test
    void directAndPreviouslyEnrichedEventsAvoidAuthLookup() throws Exception {
        OutboxEvent direct = notificationEvent(
                "person@company.com",
                null
        );
        OutboxEvent enriched = notificationEvent(null, List.of());

        assertThat(enricher.enrich(direct)).isEqualTo(direct.getPayload());
        assertThat(enricher.enrich(enriched))
                .isEqualTo(enriched.getPayload());
        verify(client, never()).getNotificationRecipients(
                List.of("OWNER", "ADMIN")
        );
    }

    @Test
    void authFailurePropagatesForExistingOutboxRetryPolicy() throws Exception {
        OutboxEvent event = notificationEvent(null, null);
        RuntimeException failure = new RuntimeException("unavailable");
        when(client.getNotificationRecipients(
                List.of("OWNER", "ADMIN")
        )).thenThrow(failure);

        assertThatThrownBy(() -> enricher.enrich(event))
                .isSameAs(failure);
    }

    private OutboxEvent notificationEvent(
            String recipient,
            List<String> recipients
    ) throws Exception {
        NotificationEvent notification = new NotificationEvent(
                "event-1",
                recipient,
                null,
                "Flag changed",
                "A flag changed",
                "EMAIL",
                recipients
        );
        return OutboxEvent.builder()
                .topic(OutboxService.NOTIFICATION_TOPIC)
                .payload(objectMapper.writeValueAsString(notification))
                .build();
    }
}
