package com.featureflag.flag_service.service;

import com.featureflag.flag_service.entity.OutboxEvent;
import com.featureflag.flag_service.observability.FlagMetrics;
import com.featureflag.flag_service.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class OutboxRetentionService {

    private static final int MAX_BATCH_SIZE = 1_000;

    private final OutboxEventRepository outboxEventRepository;
    private final Clock clock;
    private final Duration publishedAge;
    private final int batchSize;
    private final FlagMetrics flagMetrics;

    public OutboxRetentionService(
            OutboxEventRepository outboxEventRepository,
            Clock clock,
            @Value("${outbox.retention.published-age:PT168H}")
            Duration publishedAge,
            @Value("${outbox.retention.batch-size:100}")
            int batchSize,
            FlagMetrics flagMetrics
    ) {
        if (publishedAge == null
                || publishedAge.isZero()
                || publishedAge.isNegative()) {
            throw new IllegalArgumentException(
                    "outbox.retention.published-age must be positive"
            );
        }
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "outbox.retention.batch-size must be between 1 and "
                            + MAX_BATCH_SIZE
            );
        }
        this.outboxEventRepository = outboxEventRepository;
        this.clock = clock;
        this.publishedAge = publishedAge;
        this.batchSize = batchSize;
        this.flagMetrics = flagMetrics;
    }

    @Scheduled(
            initialDelayString =
                    "${outbox.retention.initial-delay-ms:60000}",
            fixedDelayString =
                    "${outbox.retention.cleanup-interval-ms:3600000}"
    )
    @Transactional
    public int deleteExpiredPublishedEvents() {
        Instant cutoff = clock.instant().minus(publishedAge);
        List<String> eligibleIds =
                outboxEventRepository.findPublishedIdsBefore(
                        OutboxEvent.STATUS_PUBLISHED,
                        cutoff,
                        PageRequest.of(0, batchSize)
                );
        if (eligibleIds.isEmpty()) {
            return 0;
        }
        int deleted = outboxEventRepository.deletePublishedByIdIn(
                OutboxEvent.STATUS_PUBLISHED,
                cutoff,
                eligibleIds
        );
        if (deleted > 0) {
            flagMetrics.outboxRetentionDeleted(deleted);
            log.info(
                    "Deleted {} expired published outbox event(s)",
                    deleted
            );
        }
        return deleted;
    }
}
