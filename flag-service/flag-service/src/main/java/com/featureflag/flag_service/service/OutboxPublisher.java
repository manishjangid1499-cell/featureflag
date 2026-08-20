package com.featureflag.flag_service.service;

import com.featureflag.flag_service.entity.OutboxEvent;
import com.featureflag.flag_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxDeliveryService outboxDeliveryService;

    @Scheduled(
            fixedDelayString =
                    "${outbox.publisher.fixed-delay-ms:1000}"
    )
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents =
                outboxEventRepository
                        .findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                                OutboxEvent.STATUS_PENDING,
                                LocalDateTime.now()
                        );

        for (OutboxEvent event : pendingEvents) {
            try {
                outboxDeliveryService.publishById(
                        event.getId()
                );
            } catch (RuntimeException exception) {
                log.error(
                        "Unexpected outbox delivery failure; "
                                + "eventId={} errorType={}",
                        event.getId(),
                        exception.getClass()
                                .getSimpleName()
                );
            }
        }
    }
}
