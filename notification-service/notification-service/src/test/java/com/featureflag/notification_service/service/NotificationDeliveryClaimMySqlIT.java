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
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
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
class NotificationDeliveryClaimMySqlIT {

    private static final Instant NOW =
            Instant.parse("2026-08-25T12:00:00Z");
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
                NOW.minusSeconds(60)
        );
        Notification second = saveDue(
                DeliveryMode.DURABLE,
                "PENDING",
                NOW.minusSeconds(60)
        );
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
                NOW.minusSeconds(60)
        );
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
                NOW.minusSeconds(60)
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
        assertEquals(NOW.plusSeconds(2 * 60), processing.getLeaseUntil());
        assertNull(processing.getNextAttemptAt());
        assertNotNull(processing.getClaimToken());
    }

    @Test
    void claimExcludesSynchronousAndLegacyRowsOnMySql() {
        Notification synchronous = saveDue(
                DeliveryMode.SYNCHRONOUS,
                "PENDING",
                NOW.minusSeconds(3 * 60)
        );
        Notification legacy = saveDue(
                null,
                "PENDING",
                NOW.minusSeconds(2 * 60)
        );
        Notification durable = saveDue(
                DeliveryMode.DURABLE,
                "PENDING",
                NOW.minusSeconds(60)
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
                NOW.minusSeconds(60)
        );
        Notification firstAtEarliestTime = saveDue(
                DeliveryMode.DURABLE,
                "PENDING",
                NOW.minusSeconds(3 * 60)
        );
        Notification secondAtEarliestTime = saveDue(
                DeliveryMode.DURABLE,
                "RETRY",
                NOW.minusSeconds(3 * 60)
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
                NOW.minusSeconds(60)
        );
        DeliveryClaim oldClaim = stateService
                .claimNextDueJob(NOW)
                .orElseThrow();
        Instant recoveryTime = NOW.plusSeconds(3 * 60);

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
                NOW.minusSeconds(2 * 60),
                2,
                "retry-token"
        );
        Notification dead = saveProcessing(
                DeliveryMode.DURABLE,
                NOW.minusSeconds(60),
                5,
                "dead-token"
        );
        Notification active = saveProcessing(
                DeliveryMode.DURABLE,
                NOW.plusSeconds(60),
                1,
                "active-token"
        );
        Notification synchronous = saveProcessing(
                DeliveryMode.SYNCHRONOUS,
                NOW.minusSeconds(60),
                1,
                "sync-token"
        );
        Notification legacy = saveProcessing(
                null,
                NOW.minusSeconds(60),
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

    @Test
    void recoveryQueryUsesProductionLeaseIndex() {
        prepareRepresentativeLeaseQueuePlan();
    }

    @Test
    void dueQueryUsesProductionDueIndex() {
        prepareRepresentativeDueQueuePlan();
    }

    private Notification saveDue(
            DeliveryMode mode,
            String status,
            Instant nextAttemptAt
    ) {
        return notificationRepository.saveAndFlush(
                notification(mode, status)
                        .nextAttemptAt(nextAttemptAt)
                        .build()
        );
    }

    private Notification saveProcessing(
            DeliveryMode mode,
            Instant leaseUntil,
            int attemptCount,
            String claimToken
    ) {
        return notificationRepository.saveAndFlush(
                notification(mode, "PROCESSING")
                        .attemptCount(attemptCount)
                        .lastAttemptAt(NOW.minusSeconds(3 * 60))
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
                ) VALUES (0, ?, ?, 'Filler', ?, ?, ?,
                          'Filler', 'EMAIL')
                """,
                IntStream.rangeClosed(1, 5_000).boxed().toList(),
                128,
                (statement, sequence) -> {
                    int category = sequence % 50;
                    statement.setTimestamp(
                            1,
                            Timestamp.from(NOW.minusSeconds(2 * 60 * 60))
                    );
                    if (category <= 5) {
                        statement.setString(2, "DURABLE");
                    } else if (category < 30) {
                        statement.setString(2, "SYNCHRONOUS");
                    } else {
                        statement.setNull(2, Types.VARCHAR);
                    }
                    statement.setTimestamp(
                            3,
                            Timestamp.from(
                                    category == 2
                                            ? NOW.plusSeconds(60 * 60)
                                            : NOW.minusSeconds(60)
                            )
                    );
                    statement.setString(
                            4,
                            "filler-" + sequence + "@company.com"
                    );
                    statement.setString(
                            5,
                            switch (category) {
                                case 1 -> "RETRY";
                                case 3 -> "PROCESSING";
                                case 4 -> "SENT";
                                case 5 -> "DEAD";
                                default -> "PENDING";
                            }
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
                Timestamp.from(NOW)
        );
        assertPlanUsesIndex(
                plan,
                "idx_notifications_delivery_due"
        );
    }

    private void prepareRepresentativeLeaseQueuePlan() {
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO notifications (
                    attempt_count,
                    claim_token,
                    created_at,
                    delivery_mode,
                    last_attempt_at,
                    lease_until,
                    message,
                    recipient,
                    status,
                    subject,
                    type
                ) VALUES (1, ?, ?, ?, ?, ?, 'Filler', ?, ?,
                          'Filler', 'EMAIL')
                """,
                IntStream.rangeClosed(1, 4_096).boxed().toList(),
                128,
                (statement, sequence) -> {
                    int category = sequence % 16;
                    boolean active = category == 1;
                    statement.setString(1, "lease-token-" + sequence);
                    statement.setTimestamp(
                            2,
                            Timestamp.from(NOW.minusSeconds(2 * 60 * 60))
                    );
                    statement.setString(
                            3,
                            category <= 5
                                    ? "DURABLE"
                                    : "SYNCHRONOUS"
                    );
                    statement.setTimestamp(
                            4,
                            Timestamp.from(NOW.minusSeconds(3 * 60))
                    );
                    statement.setTimestamp(
                            5,
                            Timestamp.from(
                                    active
                                            ? NOW.plusSeconds(5 * 60)
                                            : NOW.minusSeconds(60)
                            )
                    );
                    statement.setString(
                            6,
                            "lease-filler-" + sequence
                                    + "@company.com"
                    );
                    statement.setString(
                            7,
                            switch (category) {
                                case 2 -> "PENDING";
                                case 3 -> "RETRY";
                                case 4 -> "SENT";
                                case 5 -> "DEAD";
                                default -> "PROCESSING";
                            }
                    );
                }
        );
        jdbcTemplate.execute("ANALYZE TABLE notifications");

        List<Map<String, Object>> plan = jdbcTemplate.queryForList(
                """
                EXPLAIN SELECT n.*
                FROM notifications n
                WHERE n.delivery_mode = 'DURABLE'
                  AND n.status = 'PROCESSING'
                  AND n.lease_until <= ?
                ORDER BY n.lease_until, n.id
                LIMIT 25
                FOR UPDATE
                """,
                Timestamp.from(NOW)
        );
        assertPlanUsesIndex(
                plan,
                "idx_notifications_delivery_lease"
        );
    }

    private void assertPlanUsesIndex(
            List<Map<String, Object>> plan,
            String expectedIndex
    ) {
        Map<String, Object> selected = plan.stream()
                .filter(row -> expectedIndex.equals(row.get("key")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected index " + expectedIndex
                                + " in plan " + plan
                ));
        String extra = String.valueOf(selected.get("Extra"));
        assertFalse(extra.toLowerCase().contains("filesort"));
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
                .createdAt(NOW.minusSeconds(60 * 60))
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
            Instant nextAttemptAt
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
