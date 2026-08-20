package com.featureflag.flag_service.service;

import com.featureflag.flag_service.entity.OutboxEvent;
import com.featureflag.flag_service.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class OutboxDeliveryService {

    private static final long MAX_RETRY_DELAY_SECONDS =
            60L;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final long sendTimeoutSeconds;

    public OutboxDeliveryService(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value(
                    "${outbox.publisher.send-timeout-seconds:10}"
            )
            long sendTimeoutSeconds
    ) {
        this.outboxEventRepository =
                outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
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

        LocalDateTime now = LocalDateTime.now();

        if (event.getNextAttemptAt() != null
                && event.getNextAttemptAt()
                        .isAfter(now)) {
            return;
        }

        try {
            kafkaTemplate.send(
                    event.getTopic(),
                    event.getMessageKey(),
                    event.getPayload()
            ).get(
                    sendTimeoutSeconds,
                    TimeUnit.SECONDS
            );

            event.setStatus(
                    OutboxEvent.STATUS_PUBLISHED
            );
            event.setPublishedAt(
                    LocalDateTime.now()
            );
            event.setLastErrorType(null);

            log.info(
                    "Outbox event published; eventId={} topic={}",
                    event.getId(),
                    event.getTopic()
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
        event.setAttempts(attempts);

        long delaySeconds =
                calculateRetryDelaySeconds(attempts);

        event.setNextAttemptAt(
                LocalDateTime.now()
                        .plusSeconds(delaySeconds)
        );

        event.setLastErrorType(
                exception.getClass().getSimpleName()
        );

        log.warn(
                "Outbox publish failed; eventId={} topic={} "
                        + "attempt={} nextRetrySeconds={} "
                        + "errorType={}",
                event.getId(),
                event.getTopic(),
                attempts,
                delaySeconds,
                event.getLastErrorType()
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
