package com.featureflag.notification_service.service;

import com.featureflag.notification_service.config.NotificationDeliveryProperties;
import com.featureflag.notification_service.entity.Notification;
import com.featureflag.notification_service.repository.NotificationRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class MySqlClaimTransactionHelper {

    private static final String PROCESSING = "PROCESSING";
    private static final long LATCH_TIMEOUT_SECONDS = 15;

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryProperties properties;
    private final TransactionTemplate transactionTemplate;

    MySqlClaimTransactionHelper(
            NotificationRepository notificationRepository,
            NotificationDeliveryProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        this.notificationRepository = notificationRepository;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(
                transactionManager
        );
    }

    HeldClaim claimAndHold(
            Instant now,
            CompletableFuture<Long> lockedNotificationId,
            CountDownLatch releaseTransaction
    ) {
        HeldClaim result = transactionTemplate.execute(status -> {
            Notification notification = notificationRepository
                    .findNextDueForUpdateSkipLocked(now)
                    .orElseThrow();
            int attempts = notification.getAttemptCount() == null
                    ? 0
                    : notification.getAttemptCount();
            String claimToken = UUID.randomUUID().toString();

            notification.setStatus(PROCESSING);
            notification.setAttemptCount(attempts + 1);
            notification.setLastAttemptAt(now);
            notification.setLeaseUntil(
                    now.plus(properties.getLeaseDuration())
            );
            notification.setClaimToken(claimToken);
            notification.setNextAttemptAt(null);

            lockedNotificationId.complete(notification.getId());
            awaitRelease(releaseTransaction);

            return new HeldClaim(
                    notification.getId(),
                    claimToken,
                    attempts + 1
            );
        });

        if (result == null) {
            throw new IllegalStateException(
                    "Claim transaction returned no result"
            );
        }
        return result;
    }

    Optional<Long> findNextDueId(Instant now) {
        Optional<Long> result = transactionTemplate.execute(status ->
                notificationRepository
                        .findNextDueForUpdateSkipLocked(now)
                        .map(Notification::getId)
        );
        return result == null ? Optional.empty() : result;
    }

    private void awaitRelease(CountDownLatch releaseTransaction) {
        try {
            if (!releaseTransaction.await(
                    LATCH_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            )) {
                throw new IllegalStateException(
                        "Timed out waiting to release claim transaction"
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while holding claim transaction",
                    exception
            );
        }
    }

    record HeldClaim(
            Long notificationId,
            String claimToken,
            int attemptCount
    ) {
    }
}
