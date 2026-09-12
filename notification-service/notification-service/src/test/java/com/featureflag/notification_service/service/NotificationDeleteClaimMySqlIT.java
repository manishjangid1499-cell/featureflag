package com.featureflag.notification_service.service;

import com.featureflag.notification_service.config.NotificationDeliveryProperties;
import com.featureflag.notification_service.config.TimeConfiguration;
import com.featureflag.notification_service.entity.DeliveryMode;
import com.featureflag.notification_service.entity.Notification;
import com.featureflag.notification_service.exception.NotificationConflictException;
import com.featureflag.notification_service.repository.NotificationRepository;
import com.featureflag.notification_service.observability.NotificationMetrics;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("mysql-it")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
        NotificationService.class,
        NotificationAccessPolicy.class,
        MySqlClaimTransactionHelper.class,
        TimeConfiguration.class,
        NotificationDeleteClaimMySqlIT.PropertiesConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class NotificationDeleteClaimMySqlIT {

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
    private NotificationService notificationService;

    @Autowired
    private NotificationDeliveryProperties properties;

    @Autowired
    private MySqlClaimTransactionHelper transactionHelper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoBean
    private NotificationMetrics notificationMetrics;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE notifications");
        properties.setLeaseDuration(Duration.ofMinutes(2));
    }

    @Test
    void deleteWaitsForClaimThenRejectsProcessingJob()
            throws Exception {
        Notification pending = notificationRepository.saveAndFlush(
                Notification.builder()
                        .recipient("recipient@company.com")
                        .creatorEmail("creator@company.com")
                        .subject("Subject")
                        .message("Message")
                        .type("EMAIL")
                        .status("PENDING")
                        .createdAt(NOW.minusSeconds(60 * 60))
                        .deliveryMode(DeliveryMode.DURABLE)
                        .attemptCount(0)
                        .nextAttemptAt(NOW.minusSeconds(60))
                        .build()
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CompletableFuture<Long> lockedId = new CompletableFuture<>();
        CountDownLatch releaseClaim = new CountDownLatch(1);
        CountDownLatch deleteStarted = new CountDownLatch(1);

        try {
            Future<MySqlClaimTransactionHelper.HeldClaim> claimFuture =
                    executor.submit(() -> transactionHelper.claimAndHold(
                            NOW,
                            lockedId,
                            releaseClaim
                    ));
            assertEquals(
                    pending.getId(),
                    lockedId.get(
                            FUTURE_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    )
            );

            Future<RuntimeException> deleteFuture = executor.submit(() -> {
                deleteStarted.countDown();
                try {
                    notificationService.deleteNotification(
                            pending.getId(),
                            "owner@company.com",
                            "OWNER"
                    );
                    return null;
                } catch (RuntimeException exception) {
                    return exception;
                }
            });

            assertTrue(deleteStarted.await(
                    FUTURE_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            ));
            assertThrows(
                    TimeoutException.class,
                    () -> deleteFuture.get(500, TimeUnit.MILLISECONDS)
            );

            releaseClaim.countDown();
            claimFuture.get(
                    FUTURE_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
            RuntimeException deletionFailure = deleteFuture.get(
                    FUTURE_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );

            assertInstanceOf(
                    NotificationConflictException.class,
                    deletionFailure
            );
            Notification retained = notificationRepository
                    .findById(pending.getId())
                    .orElseThrow();
            assertEquals("PROCESSING", retained.getStatus());
        } finally {
            releaseClaim.countDown();
            executor.shutdownNow();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(
            NotificationDeliveryProperties.class
    )
    static class PropertiesConfiguration {
    }
}
