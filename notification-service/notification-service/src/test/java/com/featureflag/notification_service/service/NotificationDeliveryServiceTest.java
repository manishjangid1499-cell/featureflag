package com.featureflag.notification_service.service;

import com.featureflag.notification_service.config.NotificationDeliveryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.time.Duration;
import java.time.LocalDateTime;
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

    @Mock
    private NotificationDeliveryStateService stateService;

    @Mock
    private EmailService emailService;

    private NotificationDeliveryProperties properties;
    private NotificationDeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        properties = new NotificationDeliveryProperties();
        deliveryService = new NotificationDeliveryService(
                stateService,
                emailService,
                properties
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
        LocalDateTime before = LocalDateTime.now();

        deliveryService.processDueNotifications();

        LocalDateTime after = LocalDateTime.now();
        ArgumentCaptor<LocalDateTime> nextAttemptCaptor =
                ArgumentCaptor.forClass(LocalDateTime.class);
        verify(stateService).markRetry(
                eq(claim),
                nextAttemptCaptor.capture(),
                eq("MailSendException")
        );
        assertFalse(
                nextAttemptCaptor.getValue().isBefore(
                        before.plusSeconds(60)
                )
        );
        assertFalse(
                nextAttemptCaptor.getValue().isAfter(
                        after.plusSeconds(60)
                )
        );
        verify(stateService, never()).markDead(any(), any());
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
    }

    private DeliveryClaim claim(Long id, int attemptCount) {
        return new DeliveryClaim(
                id,
                "claim-token-" + id,
                "recipient" + id + "@company.com",
                "Subject " + id,
                "Message " + id,
                attemptCount
        );
    }
}
