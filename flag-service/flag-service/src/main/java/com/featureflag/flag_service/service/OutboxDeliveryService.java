package com.featureflag.flag_service.service;

import com.featureflag.flag_service.entity.OutboxEvent;
import com.featureflag.flag_service.observability.CorrelationIds;
import com.featureflag.flag_service.observability.FlagMetrics;
import com.featureflag.flag_service.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class OutboxDeliveryService {

    private static final long MAX_RETRY_DELAY_SECONDS =
            60L;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final long sendTimeoutSeconds;
    private final int maxAttempts;
    private final Clock clock;
    private final FlagMetrics flagMetrics;

    public OutboxDeliveryService(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value(
                    "${outbox.publisher.send-timeout-seconds:10}"
            )
            long sendTimeoutSeconds,
            @Value(
                    "${outbox.publisher.max-attempts:10}"
            )
            int maxAttempts,
            Clock clock,
            FlagMetrics flagMetrics
    ) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "outbox.publisher.max-attempts must be at least 1"
            );
        }

        this.outboxEventRepository =
                outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
        this.maxAttempts = maxAttempts;
        this.clock = clock;
        this.flagMetrics = flagMetrics;
    }

    @Transactional
    public void publishById(String eventId) {
        OutboxEvent event =
                outboxEventRepository
                        .findByIdForUpdate(eventId)
                        .orElse(null);

        if (event == null
                || !OutboxEvent.STATUS_PENDING.equals(
                        event.getStatus()
                )) {
            return;
        }

        String correlationId = CorrelationIds.resolve(
                event.getCorrelationId()
        );
        event.setCorrelationId(correlationId);

        if (event.getAttempts() >= maxAttempts) {
            event.setStatus(
                    OutboxEvent.STATUS_DEAD
            );
            flagMetrics.outboxMarkedDead(event.getTopic());
            log.error(
                    "Outbox event already exhausted retries; "
                            + "eventId={} topic={} attempts={} "
                            + "maxAttempts={} correlationId={}",
                    event.getId(),
                    event.getTopic(),
                    event.getAttempts(),
                    maxAttempts,
                    event.getCorrelationId()
            );
            return;
        }

        Instant now = clock.instant();

        if (event.getNextAttemptAt() != null
                && event.getNextAttemptAt()
                        .isAfter(now)) {
            return;
        }

        try {
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(
                            event.getTopic(),
                            event.getMessageKey(),
                            event.getPayload()
                    );
            record.headers().add(
                    CorrelationIds.HEADER_NAME,
                    correlationId.getBytes(StandardCharsets.UTF_8)
            );

            kafkaTemplate.send(record).get(
                    sendTimeoutSeconds,
                    TimeUnit.SECONDS
            );

            event.setStatus(
                    OutboxEvent.STATUS_PUBLISHED
            );
            event.setPublishedAt(
                    clock.instant()
            );
            event.setLastErrorType(null);
            flagMetrics.outboxPublished(event.getTopic());

            log.info(
                    "Outbox event published; eventId={} topic={} correlationId={}",
                    event.getId(),
                    event.getTopic(),
                    event.getCorrelationId()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordFailure(event, exception);
        } catch (Exception exception) {
            recordFailure(event, exception);
        }
    }

    private void recordFailure(
            OutboxEvent event,
            Exception exception
    ) {
        int attempts = event.getAttempts() + 1;
        flagMetrics.outboxPublishFailed(event.getTopic());
        event.setAttempts(attempts);
        event.setLastErrorType(
                exception.getClass().getSimpleName()
        );
        if (attempts >= maxAttempts) {
            event.setStatus(
                    OutboxEvent.STATUS_DEAD
            );
            flagMetrics.outboxMarkedDead(event.getTopic());
            log.error(
                    "Outbox event exhausted retries; eventId={} "
                            + "topic={} attempts={} maxAttempts={} "
                            + "errorType={} correlationId={}",
                    event.getId(),
                    event.getTopic(),
                    attempts,
                    maxAttempts,
                    event.getLastErrorType(),
                    event.getCorrelationId()
            );
            return;
        }
        long delaySeconds =
                calculateRetryDelaySeconds(attempts);
        event.setNextAttemptAt(
                clock.instant()
                        .plusSeconds(delaySeconds)
        );
        log.warn(
                "Outbox publish failed; eventId={} topic={} "
                        + "attempt={} nextRetrySeconds={} "
                        + "errorType={} correlationId={}",
                event.getId(),
                event.getTopic(),
                attempts,
                delaySeconds,
                event.getLastErrorType(),
                event.getCorrelationId()
        );
    }

    private long calculateRetryDelaySeconds(
            int attempts
    ) {
        int shift = Math.min(
                Math.max(attempts - 1, 0),
                6
        );

        long delay = 1L << shift;

        return Math.min(
                delay,
                MAX_RETRY_DELAY_SECONDS
        );
    }
}
