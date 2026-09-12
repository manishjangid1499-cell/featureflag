package com.featureflag.notification_service.service;

import com.featureflag.notification_service.config.NotificationDeliveryProperties;
import com.featureflag.notification_service.config.TimeConfiguration;
import com.featureflag.notification_service.entity.DeliveryMode;
import com.featureflag.notification_service.entity.Notification;
import com.featureflag.notification_service.repository.NotificationRepository;
import com.featureflag.notification_service.observability.NotificationMetrics;
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

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest
@ActiveProfiles("test")
@Import({
        NotificationDeliveryStateService.class,
        NotificationDeliveryService.class,
        TimeConfiguration.class,
        NotificationUnsupportedChannelDeliveryIntegrationTest.PropertiesConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationUnsupportedChannelDeliveryIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationDeliveryService deliveryService;

    @Autowired
    private NotificationDeliveryProperties properties;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private NotificationMetrics notificationMetrics;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        properties.setMaxAttempts(5);
        properties.setBatchSize(25);
        properties.setLeaseDuration(
                java.time.Duration.ofMinutes(2)
        );
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
    }

    @Test
    void unsupportedJobBecomesDeadWithoutSmtpAndEmailJobStillDelivers() {
        Instant dueAt = Instant.now().minusSeconds(60);
        Notification unsupported = save(
                "sms-recipient@company.com",
                "SMS",
                dueAt
        );
        Notification email = save(
                "email-recipient@company.com",
                "EMAIL",
                dueAt
        );

        deliveryService.processDueNotifications();

        Notification dead = notificationRepository
                .findById(unsupported.getId())
                .orElseThrow();
        assertEquals("DEAD", dead.getStatus());
        assertEquals(1, dead.getAttemptCount());
        assertNotNull(dead.getLastAttemptAt());
        assertEquals(
                "UnsupportedNotificationChannel",
                dead.getLastErrorType()
        );
        assertNull(dead.getNextAttemptAt());
        assertNull(dead.getLeaseUntil());
        assertNull(dead.getClaimToken());
        assertNull(dead.getSentAt());

        verify(emailService, never()).sendEmail(
                unsupported.getRecipient(),
                unsupported.getSubject(),
                unsupported.getMessage()
        );

        Notification sent = notificationRepository
                .findById(email.getId())
                .orElseThrow();
        assertEquals("SENT", sent.getStatus());
        assertEquals(1, sent.getAttemptCount());
        assertNotNull(sent.getSentAt());
        verify(emailService).sendEmail(
                email.getRecipient(),
                email.getSubject(),
                email.getMessage()
        );
        verify(emailService, times(1)).sendEmail(
                anyString(),
                anyString(),
                anyString()
        );
    }

    private Notification save(
            String recipient,
            String type,
            Instant dueAt
    ) {
        return notificationRepository.saveAndFlush(
                Notification.builder()
                        .recipient(recipient)
                        .subject("Subject")
                        .message("Message")
                        .type(type)
                        .status("PENDING")
                        .createdAt(dueAt.minusSeconds(60))
                        .deliveryMode(DeliveryMode.DURABLE)
                        .attemptCount(0)
                        .nextAttemptAt(dueAt)
                        .build()
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(
            NotificationDeliveryProperties.class
    )
    static class PropertiesConfiguration {
    }
}
