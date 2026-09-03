package com.featureflag.notification_service.service;

import com.featureflag.notification_service.config.NotificationDeliveryProperties;
import com.featureflag.notification_service.observability.NotificationMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-09-02T12:00:00Z");

    @Mock
    private NotificationDeliveryStateService stateService;

    @Mock
    private EmailService emailService;

    @Mock
    private NotificationMetrics notificationMetrics;

    private NotificationDeliveryProperties properties;
    private NotificationDeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        properties = new NotificationDeliveryProperties();
        deliveryService = new NotificationDeliveryService(
                stateService,
                emailService,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC),
                notificationMetrics
        );
    }

    @Test
    void successfulDeliveryIsSentOutsideTransactionAndCompleted() {
        DeliveryClaim claim = claim(1L, 1);
        when(stateService.claimNextDueJob(any()))
                .thenReturn(Optional.of(claim))
                .thenReturn(Optional.empty());
        when(stateService.markSent(eq(claim), any()))
                .thenReturn(true);
        doAnswer(invocation -> {
            assertFalse(isActualTransactionActive());
            return null;
        }).when(emailService).sendEmail(
                claim.recipient(),
                claim.subject(),
                claim.message()
        );

        deliveryService.processDueNotifications();

        verify(stateService).recoverExpiredLeases(any());
        verify(stateService).markSent(eq(claim), any());
        verify(stateService, never()).markRetry(
                any(),
                any(),
                any()
        );
        verify(stateService, never()).markDead(any(), any());
        verify(notificationMetrics).deliverySucceeded("durable");
    }

    @Test
    void failureBelowMaximumSchedulesExactRetryBackoff() {
        DeliveryClaim claim = claim(2L, 2);
        when(stateService.claimNextDueJob(any()))
                .thenReturn(Optional.of(claim))
                .thenReturn(Optional.empty());
        doThrow(new MailSendException("smtp unavailable"))
                .when(emailService)
                .sendEmail(any(), any(), any());
        when(stateService.markRetry(
                eq(claim),
                any(),
                eq("MailSendException")
        )).thenReturn(true);
        deliveryService.processDueNotifications();

        ArgumentCaptor<Instant> nextAttemptCaptor =
                ArgumentCaptor.forClass(Instant.class);
        verify(stateService).markRetry(
                eq(claim),
                nextAttemptCaptor.capture(),
                eq("MailSendException")
        );
        assertEquals(NOW.plusSeconds(60), nextAttemptCaptor.getValue());
        verify(stateService, never()).markDead(any(), any());
        verify(notificationMetrics).deliveryRetry();
    }

    @Test
    void failureAtMaximumMarksJobDead() {
        DeliveryClaim claim = claim(3L, 5);
        when(stateService.claimNextDueJob(any()))
                .thenReturn(Optional.of(claim))
                .thenReturn(Optional.empty());
        doThrow(new IllegalStateException("smtp rejected"))
                .when(emailService)
                .sendEmail(any(), any(), any());
        when(stateService.markDead(
                claim,
                "IllegalStateException"
        )).thenReturn(true);

        deliveryService.processDueNotifications();

        verify(stateService).markDead(
                claim,
                "IllegalStateException"
        );
        verify(stateService, never()).markRetry(
                any(),
                any(),
                any()
        );
        verify(notificationMetrics).deliveryDead("attempts-exhausted");
    }

    @Test
    void oneDeliveryFailureDoesNotStopTheNextJob() {
        DeliveryClaim first = claim(4L, 1);
        DeliveryClaim second = claim(5L, 1);
        when(stateService.claimNextDueJob(any()))
                .thenReturn(Optional.of(first))
                .thenReturn(Optional.of(second))
                .thenReturn(Optional.empty());
        doThrow(new MailSendException("first failed"))
                .doNothing()
                .when(emailService)
                .sendEmail(any(), any(), any());
        when(stateService.markRetry(
                eq(first),
                any(),
                eq("MailSendException")
        )).thenReturn(true);
        when(stateService.markSent(eq(second), any()))
                .thenReturn(true);

        deliveryService.processDueNotifications();

        InOrder order = inOrder(emailService, stateService);
        order.verify(emailService).sendEmail(
                first.recipient(),
                first.subject(),
                first.message()
        );
        order.verify(stateService).markRetry(
                eq(first),
                any(),
                eq("MailSendException")
        );
        order.verify(emailService).sendEmail(
                second.recipient(),
                second.subject(),
                second.message()
        );
        order.verify(stateService).markSent(
                eq(second),
                any()
        );
    }

    @Test
    void unsupportedHistoricalJobIsMarkedDeadWithoutMailAndBatchContinues() {
        DeliveryClaim unsupported = claim(11L, 1, "SMS");
        DeliveryClaim email = claim(12L, 1, "EMAIL");
        when(stateService.claimNextDueJob(any()))
                .thenReturn(Optional.of(unsupported))
                .thenReturn(Optional.of(email))
                .thenReturn(Optional.empty());
        when(stateService.markDead(
                unsupported,
                NotificationDeliveryService
                        .UNSUPPORTED_CHANNEL_ERROR_TYPE
        )).thenReturn(true);
        when(stateService.markSent(eq(email), any()))
                .thenReturn(true);

        deliveryService.processDueNotifications();

        verify(emailService, never()).sendEmail(
                unsupported.recipient(),
                unsupported.subject(),
                unsupported.message()
        );
        verify(stateService).markDead(
                unsupported,
                "UnsupportedNotificationChannel"
        );
        verify(emailService).sendEmail(
                email.recipient(),
                email.subject(),
                email.message()
        );
        verify(stateService).markSent(eq(email), any());

        InOrder order = inOrder(stateService, emailService);
        order.verify(stateService).markDead(
                unsupported,
                "UnsupportedNotificationChannel"
        );
        order.verify(emailService).sendEmail(
                email.recipient(),
                email.subject(),
                email.message()
        );
    }

    @Test
    void claimDatabaseFailureAbortsCurrentPoll() {
        RuntimeException databaseFailure =
                new RuntimeException("database unavailable");
        when(stateService.claimNextDueJob(any()))
                .thenThrow(databaseFailure);

        deliveryService.processDueNotifications();

        verify(stateService).claimNextDueJob(any());
        verify(emailService, never())
                .sendEmail(any(), any(), any());
    }

    @Test
    void completionDatabaseFailureDoesNotStopNextJob() {
        DeliveryClaim first = claim(6L, 1);
        DeliveryClaim second = claim(7L, 1);
        when(stateService.claimNextDueJob(any()))
                .thenReturn(Optional.of(first))
                .thenReturn(Optional.of(second))
                .thenReturn(Optional.empty());
        when(stateService.markSent(eq(first), any()))
                .thenThrow(new RuntimeException("completion failed"));
        when(stateService.markSent(eq(second), any()))
                .thenReturn(true);

        deliveryService.processDueNotifications();

        verify(emailService).sendEmail(
                first.recipient(),
                first.subject(),
                first.message()
        );
        verify(emailService).sendEmail(
                second.recipient(),
                second.subject(),
                second.message()
        );
        verify(stateService).markSent(eq(second), any());
    }

    @Test
    void backoffIsExponentialCappedAndOverflowSafe() {
        assertEquals(
                Duration.ofSeconds(30),
                deliveryService.calculateBackoff(1)
        );
        assertEquals(
                Duration.ofSeconds(60),
                deliveryService.calculateBackoff(2)
        );
        assertEquals(
                Duration.ofSeconds(120),
                deliveryService.calculateBackoff(3)
        );
        assertEquals(
                Duration.ofSeconds(240),
                deliveryService.calculateBackoff(4)
        );
        assertEquals(
                Duration.ofMinutes(15),
                deliveryService.calculateBackoff(20)
        );

        properties.setInitialDelay(
                Duration.ofSeconds(Long.MAX_VALUE / 2)
        );
        properties.setMaxDelay(
                Duration.ofSeconds(Long.MAX_VALUE)
        );
        properties.setMultiplier(3);

        assertEquals(
                properties.getMaxDelay(),
                deliveryService.calculateBackoff(2)
        );
    }

    @Test
    void schedulerLoopNeverClaimsMoreThanConfiguredBatchSize() {
        properties.setBatchSize(2);
        when(stateService.claimNextDueJob(any()))
                .thenReturn(Optional.of(claim(8L, 1)))
                .thenReturn(Optional.of(claim(9L, 1)));

        deliveryService.processDueNotifications();

        verify(stateService, org.mockito.Mockito.times(2))
                .claimNextDueJob(any());
    }

    @Test
    void deliveryClaimStringDoesNotExposeCoordinationOrMailData() {
        DeliveryClaim claim = claim(10L, 3);

        String renderedClaim = claim.toString();

        assertTrue(renderedClaim.contains("notificationId=10"));
        assertTrue(renderedClaim.contains("attemptCount=3"));
        assertFalse(renderedClaim.contains(claim.claimToken()));
        assertFalse(renderedClaim.contains(claim.recipient()));
        assertFalse(renderedClaim.contains(claim.subject()));
        assertFalse(renderedClaim.contains(claim.message()));
        assertFalse(renderedClaim.contains(claim.type()));
    }

    private DeliveryClaim claim(Long id, int attemptCount) {
        return claim(id, attemptCount, "EMAIL");
    }

    private DeliveryClaim claim(
            Long id,
            int attemptCount,
            String type
    ) {
        return new DeliveryClaim(
                id,
                "claim-token-" + id,
                "recipient" + id + "@company.com",
                "Subject " + id,
                "Message " + id,
                type,
                attemptCount
        );
    }
}
