package com.featureflag.notification_service.service;

import com.featureflag.notification_service.config.NotificationDeliveryProperties;
import com.featureflag.notification_service.entity.DeliveryMode;
import com.featureflag.notification_service.entity.Notification;
import com.featureflag.notification_service.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryStateService {

    static final String LEASE_EXPIRED_ERROR_TYPE =
            "DeliveryLeaseExpired";

    private static final String PROCESSING = "PROCESSING";
    private static final List<String> DUE_STATUSES =
            List.of("PENDING", "RETRY");

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryProperties properties;

    @Transactional
    public Optional<DeliveryClaim> claimNextDueJob(
            LocalDateTime now
    ) {
        List<Notification> due =
                notificationRepository.findDueForUpdate(
                        DeliveryMode.DURABLE,
                        DUE_STATUSES,
                        now,
                        PageRequest.of(0, 1)
                );

        if (due.isEmpty()) {
            return Optional.empty();
        }

        Notification notification = due.getFirst();
        int currentAttempts = notification.getAttemptCount() == null
                ? 0
                : notification.getAttemptCount();
        int claimedAttempt = currentAttempts + 1;
        String claimToken = UUID.randomUUID().toString();

        notification.setStatus(PROCESSING);
        notification.setAttemptCount(claimedAttempt);
        notification.setLastAttemptAt(now);
        notification.setLeaseUntil(
                now.plus(properties.getLeaseDuration())
        );
        notification.setClaimToken(claimToken);
        notification.setNextAttemptAt(null);

        return Optional.of(new DeliveryClaim(
                notification.getId(),
                claimToken,
                notification.getRecipient(),
                notification.getSubject(),
                notification.getMessage(),
                claimedAttempt
        ));
    }

    @Transactional
    public int recoverExpiredLeases(LocalDateTime now) {
        List<Notification> expired =
                notificationRepository.findExpiredLeasesForUpdate(
                        DeliveryMode.DURABLE,
                        PROCESSING,
                        now,
                        PageRequest.of(
                                0,
                                properties.getBatchSize()
                        )
                );

        for (Notification notification : expired) {
            int attempts = notification.getAttemptCount() == null
                    ? 0
                    : notification.getAttemptCount();

            if (attempts >= properties.getMaxAttempts()) {
                notification.setStatus("DEAD");
                notification.setNextAttemptAt(null);
            } else {
                notification.setStatus("RETRY");
                notification.setNextAttemptAt(now);
            }

            notification.setSentAt(null);
            notification.setLeaseUntil(null);
            notification.setClaimToken(null);
            notification.setLastErrorType(
                    LEASE_EXPIRED_ERROR_TYPE
            );
        }

        return expired.size();
    }

    @Transactional
    public boolean markSent(
            DeliveryClaim claim,
            LocalDateTime sentAt
    ) {
        return notificationRepository.markSentIfClaimMatches(
                claim.notificationId(),
                DeliveryMode.DURABLE,
                claim.claimToken(),
                sentAt
        ) == 1;
    }

    @Transactional
    public boolean markRetry(
            DeliveryClaim claim,
            LocalDateTime nextAttemptAt,
            String lastErrorType
    ) {
        return notificationRepository.markRetryIfClaimMatches(
                claim.notificationId(),
                DeliveryMode.DURABLE,
                claim.claimToken(),
                nextAttemptAt,
                lastErrorType
        ) == 1;
    }

    @Transactional
    public boolean markDead(
            DeliveryClaim claim,
            String lastErrorType
    ) {
        return notificationRepository.markDeadIfClaimMatches(
                claim.notificationId(),
                DeliveryMode.DURABLE,
                claim.claimToken(),
                lastErrorType
        ) == 1;
    }
}
