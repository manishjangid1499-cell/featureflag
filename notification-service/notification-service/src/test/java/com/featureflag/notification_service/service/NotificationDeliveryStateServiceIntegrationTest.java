package com.featureflag.notification_service.service;

import com.featureflag.notification_service.config.NotificationDeliveryProperties;
import com.featureflag.notification_service.entity.DeliveryMode;
import com.featureflag.notification_service.entity.Notification;
import com.featureflag.notification_service.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;

@DataJpaTest
@ActiveProfiles("test")
@Import({
        NotificationDeliveryStateService.class,
        NotificationDeliveryService.class,
        NotificationDeliveryStateServiceIntegrationTest.PropertiesConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationDeliveryStateServiceIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationDeliveryStateService stateService;

    @Autowired
    private NotificationDeliveryService deliveryService;

    @Autowired
    private NotificationDeliveryProperties properties;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        properties.setMaxAttempts(5);
        properties.setInitialDelay(java.time.Duration.ofSeconds(30));
        properties.setMultiplier(2);
        properties.setMaxDelay(java.time.Duration.ofMinutes(15));
        properties.setBatchSize(25);
        properties.setLeaseDuration(java.time.Duration.ofMinutes(2));
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
    }

    @Test
    void claimsDuePendingAndRetryJobsOneAtATime() {
        LocalDateTime now = LocalDateTime.of(
                2026,
                8,
                25,
                12,
                0
        );
        Notification pending = save(
                DeliveryMode.DURABLE,
                "PENDING",
                now.minusMinutes(2),
                null,
                0,
                null
        );
        Notification retry = save(
                DeliveryMode.DURABLE,
                "RETRY",
                now.minusMinutes(1),
                null,
                1,
                null
        );

        DeliveryClaim first = stateService
                .claimNextDueJob(now)
                .orElseThrow();
        DeliveryClaim second = stateService
                .claimNextDueJob(now)
                .orElseThrow();

        assertEquals(pending.getId(), first.notificationId());
        assertEquals(1, first.attemptCount());
        assertEquals(retry.getId(), second.notificationId());
        assertEquals(2, second.attemptCount());

        Notification claimedPending = notificationRepository
                .findById(pending.getId())
                .orElseThrow();
        assertEquals("PROCESSING", claimedPending.getStatus());
        assertEquals(1, claimedPending.getAttemptCount());
        assertEquals(now, claimedPending.getLastAttemptAt());
        assertEquals(
                now.plusMinutes(2),
                claimedPending.getLeaseUntil()
        );
        assertNull(claimedPending.getNextAttemptAt());
        assertNotNull(claimedPending.getClaimToken());
        assertEquals(36, claimedPending.getClaimToken().length());
    }

    @Test
    void normalClaimExcludesEveryIneligibleModeAndState() {
        LocalDateTime now = LocalDateTime.of(
                2026,
                8,
                25,
                12,
                0
        );
        save(
                DeliveryMode.SYNCHRONOUS,
                "PENDING",
                now.minusMinutes(1),
                null,
                0,
                null
        );
        save(
                null,
                "PENDING",
                now.minusMinutes(1),
                null,
                0,
                null
        );
        save(
                DeliveryMode.DURABLE,
                "PENDING",
                now.plusMinutes(1),
                null,
                0,
                null
        );
        save(
                DeliveryMode.DURABLE,
                "SENT",
                now.minusMinutes(1),
                null,
                1,
                null
        );
        save(
                DeliveryMode.DURABLE,
                "DEAD",
                now.minusMinutes(1),
                null,
                5,
                null
        );
        save(
                DeliveryMode.DURABLE,
                "PROCESSING",
                null,
                now.plusMinutes(1),
                1,
                "active-token"
        );

        Optional<DeliveryClaim> claim =
                stateService.claimNextDueJob(now);

        assertTrue(claim.isEmpty());
    }

    @Test
    void staleCompletionCannotOverwriteCurrentClaim() {
        LocalDateTime now = LocalDateTime.of(
                2026,
                8,
                25,
                12,
                0
        );
        Notification processing = save(
                DeliveryMode.DURABLE,
                "PROCESSING",
                null,
                now.plusMinutes(2),
                2,
                "current-token"
        );
        processing.setLastAttemptAt(now.minusSeconds(10));
        processing.setLastErrorType("MailSendException");
        notificationRepository.saveAndFlush(processing);

        boolean stale = stateService.markSent(
                claim(processing, "old-token", 2),
                now
        );
        assertFalse(stale);
        assertEquals(
                "PROCESSING",
                notificationRepository.findById(processing.getId())
                        .orElseThrow()
                        .getStatus()
        );

        boolean completed = stateService.markSent(
                claim(processing, "current-token", 2),
                now
        );

        assertTrue(completed);
        Notification sent = notificationRepository
                .findById(processing.getId())
                .orElseThrow();
        assertEquals("SENT", sent.getStatus());
        assertEquals(now, sent.getSentAt());
        assertNull(sent.getClaimToken());
        assertNull(sent.getLeaseUntil());
        assertNull(sent.getNextAttemptAt());
        assertEquals(2, sent.getAttemptCount());
        assertEquals("MailSendException", sent.getLastErrorType());
    }

    @Test
    void retryAndDeadCompletionPersistExpectedTerminalFields() {
        LocalDateTime now = LocalDateTime.of(
                2026,
                8,
                25,
                12,
                0
        );
        Notification retrying = save(
                DeliveryMode.DURABLE,
                "PROCESSING",
                null,
                now.plusMinutes(2),
                2,
                "retry-token"
        );
        Notification dying = save(
                DeliveryMode.DURABLE,
                "PROCESSING",
                null,
                now.plusMinutes(2),
                5,
                "dead-token"
        );

        assertTrue(stateService.markRetry(
                claim(retrying, "retry-token", 2),
                now.plusMinutes(1),
                "MailSendException"
        ));
        assertTrue(stateService.markDead(
                claim(dying, "dead-token", 5),
                "MailAuthenticationException"
        ));

        Notification retry = notificationRepository
                .findById(retrying.getId())
                .orElseThrow();
        assertEquals("RETRY", retry.getStatus());
        assertEquals(now.plusMinutes(1), retry.getNextAttemptAt());
        assertNull(retry.getClaimToken());
        assertNull(retry.getLeaseUntil());
        assertNull(retry.getSentAt());

        Notification dead = notificationRepository
                .findById(dying.getId())
                .orElseThrow();
        assertEquals("DEAD", dead.getStatus());
        assertNull(dead.getNextAttemptAt());
        assertNull(dead.getClaimToken());
        assertNull(dead.getLeaseUntil());
        assertNull(dead.getSentAt());
        assertEquals(5, dead.getAttemptCount());
        assertEquals(
                "MailAuthenticationException",
                dead.getLastErrorType()
        );
    }

    @Test
    void recoversOnlyExpiredDurableProcessingRows() {
        LocalDateTime now = LocalDateTime.of(
                2026,
                8,
                25,
                12,
                0
        );
        Notification retry = save(
                DeliveryMode.DURABLE,
                "PROCESSING",
                null,
                now.minusMinutes(1),
                2,
                "retry-token"
        );
        Notification dead = save(
                DeliveryMode.DURABLE,
                "PROCESSING",
                null,
                now.minusMinutes(1),
                5,
                "dead-token"
        );
        Notification active = save(
                DeliveryMode.DURABLE,
                "PROCESSING",
                null,
                now.plusMinutes(1),
                1,
                "active-token"
        );
        Notification synchronous = save(
                DeliveryMode.SYNCHRONOUS,
                "PROCESSING",
                null,
                now.minusMinutes(1),
                1,
                "sync-token"
        );
        Notification legacy = save(
                null,
                "PROCESSING",
                null,
                now.minusMinutes(1),
                1,
                "legacy-token"
        );

        int recovered = stateService.recoverExpiredLeases(now);

        assertEquals(2, recovered);
        assertRecovered(
                retry.getId(),
                "RETRY",
                now
        );
        assertRecovered(dead.getId(), "DEAD", null);
        assertEquals(
                "PROCESSING",
                notificationRepository.findById(active.getId())
                        .orElseThrow()
                        .getStatus()
        );
        assertEquals(
                "PROCESSING",
                notificationRepository.findById(synchronous.getId())
                        .orElseThrow()
                        .getStatus()
        );
        assertEquals(
                "PROCESSING",
                notificationRepository.findById(legacy.getId())
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    void leaseRecoveryIsBoundedByConfiguredBatchSize() {
        properties.setBatchSize(2);
        LocalDateTime now = LocalDateTime.of(
                2026,
                8,
                25,
                12,
                0
        );
        for (int index = 0; index < 3; index++) {
            save(
                    DeliveryMode.DURABLE,
                    "PROCESSING",
                    null,
                    now.minusMinutes(3L - index),
                    1,
                    "token-" + index
            );
        }

        int recovered = stateService.recoverExpiredLeases(now);

        assertEquals(2, recovered);
        long stillProcessing = notificationRepository.findAll()
                .stream()
                .filter(notification ->
                        "PROCESSING".equals(
                                notification.getStatus()
                        )
                )
                .count();
        assertEquals(1, stillProcessing);
    }

    @Test
    void realClaimTransactionCommitsBeforeSmtpCall() {
        LocalDateTime dueAt = LocalDateTime.now().minusMinutes(1);
        Notification notification = save(
                DeliveryMode.DURABLE,
                "PENDING",
                dueAt,
                null,
                0,
                null
        );
        doAnswer(invocation -> {
            assertFalse(
                    TransactionSynchronizationManager
                            .isActualTransactionActive()
            );
            Notification claimed = notificationRepository
                    .findById(notification.getId())
                    .orElseThrow();
            assertEquals("PROCESSING", claimed.getStatus());
            return null;
        }).when(emailService).sendEmail(
                notification.getRecipient(),
                notification.getSubject(),
                notification.getMessage()
        );

        deliveryService.processDueNotifications();

        Notification sent = notificationRepository
                .findById(notification.getId())
                .orElseThrow();
        assertEquals("SENT", sent.getStatus());
        assertNotNull(sent.getSentAt());
    }

    private Notification save(
            DeliveryMode deliveryMode,
            String status,
            LocalDateTime nextAttemptAt,
            LocalDateTime leaseUntil,
            Integer attemptCount,
            String claimToken
    ) {
        return notificationRepository.saveAndFlush(
                Notification.builder()
                        .recipient("recipient@company.com")
                        .subject("Subject")
                        .message("Message")
                        .type("EMAIL")
                        .status(status)
                        .createdAt(LocalDateTime.now().minusHours(1))
                        .deliveryMode(deliveryMode)
                        .attemptCount(attemptCount)
                        .nextAttemptAt(nextAttemptAt)
                        .leaseUntil(leaseUntil)
                        .claimToken(claimToken)
                        .build()
        );
    }

    private DeliveryClaim claim(
            Notification notification,
            String claimToken,
            int attemptCount
    ) {
        return new DeliveryClaim(
                notification.getId(),
                claimToken,
                notification.getRecipient(),
                notification.getSubject(),
                notification.getMessage(),
                attemptCount
        );
    }

    private void assertRecovered(
            Long id,
            String status,
            LocalDateTime nextAttemptAt
    ) {
        Notification notification = notificationRepository
                .findById(id)
                .orElseThrow();
        assertEquals(status, notification.getStatus());
        assertEquals(
                nextAttemptAt,
                notification.getNextAttemptAt()
        );
        assertNull(notification.getLeaseUntil());
        assertNull(notification.getClaimToken());
        assertEquals(
                NotificationDeliveryStateService
                        .LEASE_EXPIRED_ERROR_TYPE,
                notification.getLastErrorType()
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(
            NotificationDeliveryProperties.class
    )
    static class PropertiesConfiguration {
    }
}
