package com.featureflag.flag_service.service;

import com.featureflag.flag_service.entity.OutboxEvent;
import com.featureflag.flag_service.observability.CorrelationIds;
import com.featureflag.flag_service.observability.FlagMetrics;
import com.featureflag.flag_service.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class OutboxDeliveryServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-08-27T12:00:00Z");
    private static final Clock CLOCK =
            Clock.fixed(NOW, ZoneOffset.UTC);

    private final OutboxEventRepository repository =
            mock(OutboxEventRepository.class);

    private final KafkaTemplate<String, String> kafkaTemplate =
            mock(KafkaTemplate.class);

    private final FlagMetrics flagMetrics = mock(FlagMetrics.class);

    private final OutboxPayloadEnricher outboxPayloadEnricher =
            mock(OutboxPayloadEnricher.class);

    private final OutboxDeliveryService service =
            new OutboxDeliveryService(
                    repository,
                    kafkaTemplate,
                    1L,
                    10,
                    CLOCK,
                    flagMetrics,
                    outboxPayloadEnricher
            );

    @BeforeEach
    void passThroughPayload() {
        when(outboxPayloadEnricher.enrich(any(OutboxEvent.class)))
                .thenAnswer(invocation ->
                        invocation.<OutboxEvent>getArgument(0).getPayload()
                );
    }

    @Test
    void successfulKafkaAckMarksEventPublished()
            throws Exception {

        OutboxEvent event = pendingEvent();

        when(repository.findByIdForUpdate(event.getId()))
                .thenReturn(Optional.of(event));

        CompletableFuture<SendResult<String, String>>
                future =
                CompletableFuture.completedFuture(
                        mock(SendResult.class)
                );

        when(
                kafkaTemplate.send(any(ProducerRecord.class))
        ).thenReturn(future);

        service.publishById(event.getId());

        assertThat(event.getStatus())
                .isEqualTo(
                        OutboxEvent.STATUS_PUBLISHED
                );
        assertThat(event.getPublishedAt())
                .isNotNull();
        assertThat(event.getLastErrorType())
                .isNull();
        org.mockito.ArgumentCaptor<ProducerRecord<String, String>> recordCaptor =
                org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, String> sent = recordCaptor.getValue();
        assertThat(sent.topic()).isEqualTo(event.getTopic());
        assertThat(sent.key()).isEqualTo(event.getMessageKey());
        assertThat(sent.value()).isEqualTo(event.getPayload());
        assertThat(sent.headers().lastHeader(CorrelationIds.HEADER_NAME))
                .isNotNull();
        assertThat(event.getCorrelationId()).hasSize(36);
        verify(flagMetrics).outboxPublished(event.getTopic());
    }

    @Test
    void failedKafkaAckKeepsEventPendingForRetry()
            throws Exception {

        OutboxEvent event = pendingEvent();

        when(repository.findByIdForUpdate(event.getId()))
                .thenReturn(Optional.of(event));

        CompletableFuture<SendResult<String, String>>
                future = new CompletableFuture<>();

        future.completeExceptionally(
                new RuntimeException(
                        "broker unavailable"
                )
        );

        when(
                kafkaTemplate.send(any(ProducerRecord.class))
        ).thenReturn(future);

        service.publishById(event.getId());

        assertThat(event.getStatus())
                .isEqualTo(
                        OutboxEvent.STATUS_PENDING
                );
        assertThat(event.getAttempts())
                .isEqualTo(1);
        assertThat(event.getNextAttemptAt())
                .isEqualTo(NOW.plusSeconds(1));
        assertThat(event.getLastErrorType())
                .isNotBlank();
    }

    @Test
    void recipientEnrichmentFailureUsesOutboxRetryWithoutKafkaSend() {
        OutboxEvent event = pendingEvent();
        event.setTopic(OutboxService.NOTIFICATION_TOPIC);
        when(repository.findByIdForUpdate(event.getId()))
                .thenReturn(Optional.of(event));
        when(outboxPayloadEnricher.enrich(event))
                .thenThrow(new IllegalStateException("auth unavailable"));

        service.publishById(event.getId());

        assertThat(event.getStatus())
                .isEqualTo(OutboxEvent.STATUS_PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt())
                .isEqualTo(NOW.plusSeconds(1));
        assertThat(event.getLastErrorType())
                .isEqualTo("IllegalStateException");
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    @Test
    void failedKafkaAckAtMaxAttemptsMarksEventDead() {
        OutboxEvent event = pendingEvent();
        // Nine previous failed deliveries.
        // This failure is the tenth and should be terminal.
        event.setAttempts(9);
        when(
                repository.findByIdForUpdate(
                        event.getId()
                )
        ).thenReturn(
                Optional.of(event)
        );
        CompletableFuture<SendResult<String, String>>
                future =
                new CompletableFuture<>();
        future.completeExceptionally(
                new RuntimeException(
                        "broker unavailable"
                )
        );
        when(
                kafkaTemplate.send(any(ProducerRecord.class))
        ).thenReturn(future);
        service.publishById(
                event.getId()
        );
        assertThat(event.getStatus())
                .isEqualTo(
                        OutboxEvent.STATUS_DEAD
                );
        assertThat(event.getAttempts())
                .isEqualTo(10);
        assertThat(event.getPublishedAt())
                .isNull();
        assertThat(event.getLastErrorType())
                .isNotBlank();
        verify(flagMetrics).outboxMarkedDead(event.getTopic());
    }

    @Test
    void deadEventIsNotSentAgain() {
        OutboxEvent event = pendingEvent();
        event.setStatus(
                OutboxEvent.STATUS_DEAD
        );
        when(
                repository.findByIdForUpdate(
                        event.getId()
                )
        ).thenReturn(
                Optional.of(event)
        );
        service.publishById(
                event.getId()
        );
        verify(
                kafkaTemplate,
                never()
        ).send(any(ProducerRecord.class));
    }
    @Test
    void configuredMaxAttemptsControlsDeadTransition() {
        OutboxDeliveryService threeAttemptService =
                new OutboxDeliveryService(
                        repository,
                        kafkaTemplate,
                        1L,
                        3,
                        CLOCK,
                        flagMetrics,
                        outboxPayloadEnricher
                );
        OutboxEvent event = pendingEvent();
        event.setAttempts(2);
        when(
                repository.findByIdForUpdate(
                        event.getId()
                )
        ).thenReturn(
                Optional.of(event)
        );
        CompletableFuture<SendResult<String, String>>
                future =
                new CompletableFuture<>();
        future.completeExceptionally(
                new RuntimeException(
                        "broker unavailable"
                )
        );
        when(
                kafkaTemplate.send(any(ProducerRecord.class))
        ).thenReturn(future);
        threeAttemptService.publishById(
                event.getId()
        );
        assertThat(event.getStatus())
                .isEqualTo(
                        OutboxEvent.STATUS_DEAD
                );
        assertThat(event.getAttempts())
                .isEqualTo(3);
    }

    @Test
    void alreadyExhaustedPendingEventIsMarkedDeadWithoutSending() {
        OutboxEvent event = pendingEvent();
        event.setAttempts(10);
        when(
                repository.findByIdForUpdate(
                        event.getId()
                )
        ).thenReturn(
                Optional.of(event)
        );
        service.publishById(
                event.getId()
        );
        assertThat(event.getStatus())
                .isEqualTo(
                        OutboxEvent.STATUS_DEAD
                );
        assertThat(event.getAttempts())
                .isEqualTo(10);
        assertThat(event.getPublishedAt())
                .isNull();
        verify(
                kafkaTemplate,
                never()
        ).send(any(ProducerRecord.class));
    }
    @Test
    void alreadyPublishedEventIsNotSentAgain() {
        OutboxEvent event = pendingEvent();
        event.setStatus(
                OutboxEvent.STATUS_PUBLISHED
        );

        when(repository.findByIdForUpdate(event.getId()))
                .thenReturn(Optional.of(event));

        service.publishById(event.getId());

        verify(
                kafkaTemplate,
                never()
        ).send(any(ProducerRecord.class));
    }

    private OutboxEvent pendingEvent() {
        Instant now = NOW.minusSeconds(1);

        return OutboxEvent.builder()
                .id(
                        "11111111-1111-1111-1111-111111111111"
                )
                .topic("feature-flag-events")
                .messageKey("checkout")
                .eventType("FLAG_UPDATED")
                .payload(
                        "{\"eventId\":\"11111111-1111-1111-1111-111111111111\"}"
                )
                .status(
                        OutboxEvent.STATUS_PENDING
                )
                .attempts(0)
                .createdAt(now)
                .nextAttemptAt(now)
                .build();
    }
}
