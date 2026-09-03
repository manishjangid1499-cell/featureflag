package com.featureflag.notification_service.service;

import com.featureflag.notification_service.config.NotificationDeliveryProperties;
import com.featureflag.notification_service.validation.NotificationTypePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDeliveryService {

    private static final int LAST_ERROR_TYPE_MAX_LENGTH = 128;
    static final String UNSUPPORTED_CHANNEL_ERROR_TYPE =
            "UnsupportedNotificationChannel";

    private final NotificationDeliveryStateService stateService;
    private final EmailService emailService;
    private final NotificationDeliveryProperties properties;
    private final Clock clock;

    public void processDueNotifications() {
        try {
            int recovered = stateService.recoverExpiredLeases(
                    clock.instant()
            );
            if (recovered > 0) {
                log.info(
                        "Recovered expired notification delivery leases; count={}",
                        recovered
                );
            }
        } catch (RuntimeException exception) {
            log.error(
                    "Notification delivery lease recovery failed; errorType={}",
                    safeErrorType(exception)
            );
            return;
        }

        for (int processed = 0;
             processed < properties.getBatchSize();
             processed++) {
            Optional<DeliveryClaim> claim;

            try {
                claim = stateService.claimNextDueJob(
                        clock.instant()
                );
            } catch (RuntimeException exception) {
                log.error(
                        "Notification delivery claim failed; errorType={}",
                        safeErrorType(exception)
                );
                return;
            }

            if (claim.isEmpty()) {
                return;
            }

            deliver(claim.get());
        }
    }

    private void deliver(DeliveryClaim claim) {
        if (!NotificationTypePolicy.isEmail(claim.type())) {
            completeUnsupportedChannel(claim);
            return;
        }

        try {
            emailService.sendEmail(
                    claim.recipient(),
                    claim.subject(),
                    claim.message()
            );
        } catch (Exception exception) {
            completeFailure(claim, exception);
            return;
        }

        try {
            boolean completed = stateService.markSent(
                    claim,
                    clock.instant()
            );

            if (completed) {
                log.info(
                        "Notification delivery completed; notificationId={} attempt={} status=SENT",
                        claim.notificationId(),
                        claim.attemptCount()
                );
            } else {
                log.warn(
                        "Notification delivery success ignored for stale claim; notificationId={} attempt={}",
                        claim.notificationId(),
                        claim.attemptCount()
                );
            }
        } catch (RuntimeException exception) {
            log.error(
                    "Notification delivery success could not be persisted; notificationId={} attempt={} errorType={}",
                    claim.notificationId(),
                    claim.attemptCount(),
                    safeErrorType(exception)
            );
        }
    }

    private void completeUnsupportedChannel(
            DeliveryClaim claim
    ) {
        try {
            boolean completed = stateService.markDead(
                    claim,
                    UNSUPPORTED_CHANNEL_ERROR_TYPE
            );

            if (completed) {
                log.warn(
                        "Notification marked DEAD due to unsupported notification channel; notificationId={} attempt={}",
                        claim.notificationId(),
                        claim.attemptCount()
                );
            } else {
                log.warn(
                        "Unsupported notification channel ignored for stale claim; notificationId={} attempt={}",
                        claim.notificationId(),
                        claim.attemptCount()
                );
            }
        } catch (RuntimeException exception) {
            log.error(
                    "Unsupported notification channel state could not be persisted; notificationId={} attempt={} errorType={}",
                    claim.notificationId(),
                    claim.attemptCount(),
                    safeErrorType(exception)
            );
        }
    }

    private void completeFailure(
            DeliveryClaim claim,
            Exception exception
    ) {
        String errorType = safeErrorType(exception);

        try {
            boolean completed;
            String targetStatus;

            if (claim.attemptCount()
                    >= properties.getMaxAttempts()) {
                targetStatus = "DEAD";
                completed = stateService.markDead(
                        claim,
                        errorType
                );
            } else {
                targetStatus = "RETRY";
                Instant nextAttemptAt =
                        clock.instant().plus(
                                calculateBackoff(
                                        claim.attemptCount()
                                )
                        );
                completed = stateService.markRetry(
                        claim,
                        nextAttemptAt,
                        errorType
                );
            }

            if (completed) {
                log.warn(
                        "Notification delivery failed; notificationId={} attempt={} status={} errorType={}",
                        claim.notificationId(),
                        claim.attemptCount(),
                        targetStatus,
                        errorType
                );
            } else {
                log.warn(
                        "Notification delivery failure ignored for stale claim; notificationId={} attempt={} errorType={}",
                        claim.notificationId(),
                        claim.attemptCount(),
                        errorType
                );
            }
        } catch (RuntimeException completionException) {
            log.error(
                    "Notification delivery failure could not be persisted; notificationId={} attempt={} errorType={}",
                    claim.notificationId(),
                    claim.attemptCount(),
                    safeErrorType(completionException)
            );
        }
    }

    Duration calculateBackoff(int attemptCount) {
        Duration maxDelay = properties.getMaxDelay();
        Duration delay = properties.getInitialDelay();

        for (int attempt = 1;
             attempt < attemptCount;
             attempt++) {
            if (delay.compareTo(maxDelay) >= 0) {
                return maxDelay;
            }

            try {
                delay = delay.multipliedBy(
                        properties.getMultiplier()
                );
            } catch (ArithmeticException exception) {
                return maxDelay;
            }

            if (delay.compareTo(maxDelay) > 0) {
                return maxDelay;
            }
        }

        return delay;
    }

    private String safeErrorType(Throwable throwable) {
        String errorType = throwable.getClass().getSimpleName();
        if (errorType == null || errorType.isBlank()) {
            errorType = "Exception";
        }

        return errorType.length() <= LAST_ERROR_TYPE_MAX_LENGTH
                ? errorType
                : errorType.substring(
                        0,
                        LAST_ERROR_TYPE_MAX_LENGTH
                );
    }
}
