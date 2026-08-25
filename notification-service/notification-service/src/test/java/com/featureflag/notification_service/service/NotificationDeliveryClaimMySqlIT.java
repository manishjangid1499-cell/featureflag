package com.featureflag.notification_service.service;

import com.featureflag.notification_service.config.NotificationDeliveryProperties;
import com.featureflag.notification_service.entity.DeliveryMode;
import com.featureflag.notification_service.entity.Notification;
import com.featureflag.notification_service.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("mysql-it")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
        NotificationDeliveryStateService.class,
        MySqlClaimTransactionHelper.class,
        NotificationDeliveryClaimMySqlIT.PropertiesConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationDeliveryClaimMySqlIT {

    private static final LocalDateTime NOW = LocalDateTime.of(
            2026,
            8,
            25,
            12,
            0
    );
    private static final long FUTURE_TIMEOUT_SECONDS = 15;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11");

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationDeliveryStateService stateService;

    @Autowired
    private NotificationDeliveryProperties properties;

    @Autowired
    private MySqlClaimTransactionHelper transactionHelper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    void createTestOnlyDueQueueIndex() {
        jdbcTemplate.execute("""
                CREATE INDEX idx_test_notifications_delivery_due
                ON notifications (
                    delivery_mode,
                    next_attempt_at,
                    id,
                    status
                )
                """);
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE notifications");
        properties.setMaxAttempts(5);
        properties.setBatchSize(25);
        properties.setLeaseDuration(Duration.ofMinutes(2));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("TRUNCATE TABLE notifications");
    }

    @Test
    void twoOverlappingWorkersClaimDifferentDueRows() throws Exception {
        Notification first = saveDue(
                DeliveryMode.DURABLE,
                "PENDING",
                NOW.minusMinutes(1)
        );
        Notification second = saveDue(
                DeliveryMode.DURABLE,
                "PENDING",
                NOW.minusMinutes(1)
        );
        prepareRepresentativeDueQueuePlan();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CompletableFuture<Long> firstLockedId =
                new CompletableFuture<>();
        CompletableFuture<Long> secondLockedId =
                new CompletableFuture<>();
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);

        try {
            Future<MySqlClaimTransactionHelper.HeldClaim> firstFuture =
                    executor.submit(() -> transactionHelper.claimAndHold(
                            NOW,
                            firstLockedId,
                            releaseFirst
                    ));
            Long workerAId = firstLockedId.get(
                    FUTURE_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );

            Future<MySqlClaimTransactionHelper.HeldClaim> secondFuture =
                    executor.submit(() -> transactionHelper.claimAndHold(
                            NOW,
                            secondLockedId,
                            releaseSecond
                    ));
            Long workerBId = secondLockedId.get(
                    FUTURE_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );

            assertEquals(first.getId(), workerAId);
            assertEquals(second.getId(), workerBId);
            assertNotEquals(workerAId, workerBId);
            assertEquals(1L, releaseFirst.getCount());
            assertEquals(1L, releaseSecond.getCount());

            releaseFirst.countDown();
            releaseSecond.countDown();
            MySqlClaimTransactionHelper.HeldClaim workerA =
                    firstFuture.get(
                            FUTURE_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    );
            MySqlClaimTransactionHelper.HeldClaim workerB =
                    secondFuture.get(
                            FUTURE_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    );

            assertClaimed(workerA.notificationId(), workerA.claimToken());
            assertClaimed(workerB.notificationId(), workerB.claimToken());
            assertNotEquals(workerA.claimToken(), workerB.claimToken());
        } finally {
            releaseFirst.countDown();
            releaseSecond.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void secondWorkerReturnsEmptyBeforeFirstReleasesOnlyRow()
            throws Exception {
        Notification only = saveDue(
                DeliveryMode.DURABLE,
                "PENDING",
                NOW.minusMinutes(1)
        );
        prepareRepresentativeDueQueuePlan();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CompletableFuture<Long> lockedId = new CompletableFuture<>();
        CountDownLatch releaseFirst = new CountDownLatch(1);

        try {
            Future<MySqlClaimTransactionHelper.HeldClaim> firstFuture =
                    executor.submit(() -> transactionHelper.claimAndHold(
                            NOW,
                            lockedId,
                            releaseFirst
                    ));
            assertEquals(
                    only.getId(),
                    lockedId.get(
                            FUTURE_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    )
            );

            Future<Optional<Long>> secondFuture = executor.submit(() ->
                    transactionHelper.findNextDueId(NOW)
            );
            Optional<Long> secondResult = secondFuture.get(
                    5,
                    TimeUnit.SECONDS
            );

            assertTrue(secondResult.isEmpty());
            assertEquals(1L, releaseFirst.getCount());

            releaseFirst.countDown();
            firstFuture.get(
                    FUTURE_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );

            assertTrue(stateService.claimNextDueJob(NOW).isEmpty());
            assertEquals(
                    "PROCESSING",
                    notificationRepository.findById(only.getId())
                            .orElseThrow()
                            .getStatus()
            );
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void productionStateServiceClaimsDueJobOnMySql() {
        Notification due = saveDue(
                DeliveryMode.DURABLE,
                "PENDING",
                NOW.minusMinutes(1)
        );

        DeliveryClaim claim = stateService
                .claimNextDueJob(NOW)
                .orElseThrow();

        assertEquals(due.getId(), claim.notificationId());
        assertEquals(1, claim.attemptCount());
        Notification processing = notificationRepository
                .findById(due.getId())
                .orElseThrow();
        assertEquals("PROCESSING", processing.getStatus());
        assertEquals(1, processing.getAttemptCount());
        assertEquals(NOW, processing.getLastAttemptAt());
        assertEquals(NOW.plusMinutes(2), processing.getLeaseUntil());
        assertNull(processing.getNextAttemptAt());
        assertNotNull(processing.getClaimToken());
    }

    @Test
    void claimExcludesSynchronousAndLegacyRowsOnMySql() {
        Notification synchronous = saveDue(
                DeliveryMode.SYNCHRONOUS,
                "PENDING",
                NOW.minusMinutes(3)
        );
        Notification legacy = saveDue(
                null,
                "PENDING",
                NOW.minusMinutes(2)
        );
        Notification durable = saveDue(
                DeliveryMode.DURABLE,
                "PENDING",
                NOW.minusMinutes(1)
        );

        DeliveryClaim claim = stateService
                .claimNextDueJob(NOW)
                .orElseThrow();

        assertEquals(durable.getId(), claim.notificationId());
        assertEquals(
                "PENDING",
                notificationRepository.findById(synchronous.getId())
                        .orElseThrow()
                        .getStatus()
        );
        assertEquals(
                "PENDING",
                notificationRepository.findById(legacy.getId())
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    void claimOrdersByDueTimeThenIdOnMySql() {
        Notification later = saveDue(
                DeliveryMode.DURABLE,
                "PENDING",
                NOW.minusMinutes(1)
        );
        Notification firstAtEarliestTime = saveDue(
                DeliveryMode.DURABLE,
                "PENDING",
                NOW.minusMinutes(3)
        );
        Notification secondAtEarliestTime = saveDue(
                DeliveryMode.DURABLE,
                "RETRY",
                NOW.minusMinutes(3)
        );

        List<Long> claimedIds = List.of(
                stateService.claimNextDueJob(NOW)
                        .orElseThrow()
                        .notificationId(),
                stateService.claimNextDueJob(NOW)
                        .orElseThrow()
                        .notificationId(),
                stateService.claimNextDueJob(NOW)
                        .orElseThrow()
                        .notificationId()
        );

        assertEquals(
                List.of(
                        firstAtEarliestTime.getId(),
                        secondAtEarliestTime.getId(),
                        later.getId()
                ),
                claimedIds
        );
    }

    @Test
    void staleTokenCannotCompleteReclaimedJobOnMySql() {
        Notification due = saveDue(
                DeliveryMode.DURABLE,
                "PENDING",
                NOW.minusMinutes(1)
        );
        DeliveryClaim oldClaim = stateService
                .claimNextDueJob(NOW)
                .orElseThrow();
        LocalDateTime recoveryTime = NOW.plusMinutes(3);

        assertEquals(1, stateService.recoverExpiredLeases(recoveryTime));
        DeliveryClaim currentClaim = stateService
                .claimNextDueJob(recoveryTime)
                .orElseThrow();

        assertNotEquals(oldClaim.claimToken(), currentClaim.claimToken());
        assertFalse(stateService.markSent(oldClaim, recoveryTime));

        Notification current = notificationRepository
                .findById(due.getId())
                .orElseThrow();
        assertEquals("PROCESSING", current.getStatus());
        assertEquals(currentClaim.claimToken(), current.getClaimToken());
        assertEquals(2, current.getAttemptCount());
    }

    @Test
    void leaseRecoveryUsesMySqlAndExcludesIneligibleRows() {
        Notification retry = saveProcessing(
                DeliveryMode.DURABLE,
                NOW.minusMinutes(2),
                2,
                "retry-token"
        );
        Notification dead = saveProcessing(
                DeliveryMode.DURABLE,
                NOW.minusMinutes(1),
                5,
                "dead-token"
        );
        Notification active = saveProcessing(
                DeliveryMode.DURABLE,
                NOW.plusMinutes(1),
                1,
                "active-token"
        );
        Notification synchronous = saveProcessing(
                DeliveryMode.SYNCHRONOUS,
                NOW.minusMinutes(1),
                1,
                "sync-token"
        );
        Notification legacy = saveProcessing(
                null,
                NOW.minusMinutes(1),
                1,
                "legacy-token"
        );

        assertEquals(2, stateService.recoverExpiredLeases(NOW));

        assertRecovered(retry.getId(), "RETRY", NOW);
        assertRecovered(dead.getId(), "DEAD", null);
        assertProcessing(active.getId(), "active-token");
        assertProcessing(synchronous.getId(), "sync-token");
        assertProcessing(legacy.getId(), "legacy-token");
    }

    private Notification saveDue(
            DeliveryMode mode,
            String status,
            LocalDateTime nextAttemptAt
    ) {
        return notificationRepository.saveAndFlush(
                notification(mode, status)
                        .nextAttemptAt(nextAttemptAt)
                        .build()
        );
    }

    private Notification saveProcessing(
            DeliveryMode mode,
            LocalDateTime leaseUntil,
            int attemptCount,
            String claimToken
    ) {
        return notificationRepository.saveAndFlush(
                notification(mode, "PROCESSING")
                        .attemptCount(attemptCount)
                        .lastAttemptAt(NOW.minusMinutes(3))
                        .leaseUntil(leaseUntil)
                        .claimToken(claimToken)
                        .build()
        );
    }

    private void prepareRepresentativeDueQueuePlan() {
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO notifications (
                    attempt_count,
                    created_at,
                    delivery_mode,
                    message,
                    next_attempt_at,
                    recipient,
                    status,
                    subject,
                    type
                ) VALUES (0, ?, 'SYNCHRONOUS', 'Filler', ?, ?,
                          'PENDING', 'Filler', 'EMAIL')
                """,
                IntStream.rangeClosed(1, 512).boxed().toList(),
                128,
                (statement, sequence) -> {
                    statement.setTimestamp(
                            1,
                            Timestamp.valueOf(NOW.minusHours(2))
                    );
                    statement.setTimestamp(
                            2,
                            Timestamp.valueOf(NOW.minusMinutes(1))
                    );
                    statement.setString(
                            3,
                            "filler-" + sequence + "@company.com"
                    );
                }
        );
        jdbcTemplate.execute("ANALYZE TABLE notifications");

        List<Map<String, Object>> plan = jdbcTemplate.queryForList(
                """
                EXPLAIN SELECT n.*
                FROM notifications n
                WHERE n.delivery_mode = 'DURABLE'
                  AND n.status IN ('PENDING', 'RETRY')
                  AND n.next_attempt_at <= ?
                ORDER BY n.next_attempt_at, n.id
                LIMIT 1
                FOR UPDATE SKIP LOCKED
                """,
                Timestamp.valueOf(NOW)
        );
        assertTrue(plan.stream().anyMatch(row ->
                "idx_test_notifications_delivery_due".equals(
                        row.get("key")
                )
        ));
    }

    private Notification.NotificationBuilder notification(
            DeliveryMode mode,
            String status
    ) {
        return Notification.builder()
                .recipient("recipient@company.com")
                .subject("Subject")
                .message("Message")
                .type("EMAIL")
                .status(status)
                .createdAt(NOW.minusHours(1))
                .deliveryMode(mode)
                .attemptCount(0);
    }

    private void assertClaimed(Long id, String claimToken) {
        Notification notification = notificationRepository
                .findById(id)
                .orElseThrow();
        assertEquals("PROCESSING", notification.getStatus());
        assertEquals(1, notification.getAttemptCount());
        assertEquals(claimToken, notification.getClaimToken());
        assertNotNull(notification.getLeaseUntil());
        assertNull(notification.getNextAttemptAt());
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
        assertEquals(nextAttemptAt, notification.getNextAttemptAt());
        assertNull(notification.getLeaseUntil());
        assertNull(notification.getClaimToken());
        assertEquals(
                NotificationDeliveryStateService
                        .LEASE_EXPIRED_ERROR_TYPE,
                notification.getLastErrorType()
        );
    }

    private void assertProcessing(Long id, String claimToken) {
        Notification notification = notificationRepository
                .findById(id)
                .orElseThrow();
        assertEquals("PROCESSING", notification.getStatus());
        assertEquals(claimToken, notification.getClaimToken());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(
            NotificationDeliveryProperties.class
    )
    static class PropertiesConfiguration {
    }
}
