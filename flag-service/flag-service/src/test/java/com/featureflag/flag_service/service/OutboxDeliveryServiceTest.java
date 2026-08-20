package com.featureflag.flag_service.service;

import com.featureflag.flag_service.entity.OutboxEvent;
import com.featureflag.flag_service.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxDeliveryServiceTest {

    private final OutboxEventRepository repository =
            mock(OutboxEventRepository.class);

    private final KafkaTemplate<String, String> kafkaTemplate =
            mock(KafkaTemplate.class);

    private final OutboxDeliveryService service =
            new OutboxDeliveryService(
                    repository,
                    kafkaTemplate,
                    1L
            );

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
                kafkaTemplate.send(
                        event.getTopic(),
                        event.getMessageKey(),
                        event.getPayload()
                )
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
                kafkaTemplate.send(
                        event.getTopic(),
                        event.getMessageKey(),
                        event.getPayload()
                )
        ).thenReturn(future);

        LocalDateTime before =
                LocalDateTime.now();

        service.publishById(event.getId());

        assertThat(event.getStatus())
                .isEqualTo(
                        OutboxEvent.STATUS_PENDING
                );
        assertThat(event.getAttempts())
                .isEqualTo(1);
        assertThat(event.getNextAttemptAt())
                .isAfter(before);
        assertThat(event.getLastErrorType())
                .isNotBlank();
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
        ).send(
                event.getTopic(),
                event.getMessageKey(),
                event.getPayload()
        );
    }

    private OutboxEvent pendingEvent() {
        LocalDateTime now =
                LocalDateTime.now().minusSeconds(1);

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
