package com.featureflag.notification_service.service;

import com.featureflag.notification_service.dto.InvitationEmailRequest;
import com.featureflag.notification_service.entity.DeliveryMode;
import com.featureflag.notification_service.entity.Notification;
import com.featureflag.notification_service.exception.InvitationDeliveryException;
import com.featureflag.notification_service.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationEmailServiceTest {

    private static final String RAW_TOKEN =
            "VERY_SECRET_TEST_TOKEN";

    private static final String ACCEPTANCE_URL =
            "https://frontend.example.test/accept-invitation?token="
                    + RAW_TOKEN;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private InvitationEmailService invitationEmailService;

    private InvitationEmailRequest request;
    private List<String> persistedStatuses;

    @BeforeEach
    void setUp() {
        persistedStatuses = new ArrayList<>();

        request = new InvitationEmailRequest(
                " Invitee@Company.com ",
                "Invitee",
                "Owner",
                "owner@company.com",
                "DEVELOPER",
                48,
                ACCEPTANCE_URL
        );

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification notification =
                            invocation.getArgument(0);
                    persistedStatuses.add(notification.getStatus());
                    if (notification.getId() == null) {
                        notification.setId(100L);
                    }
                    return notification;
                });
    }

    @Test
    void rawInvitationTokenIsUsedForEmailButNeverPersistedInNotificationHistory() {
        Notification result =
                invitationEmailService.sendInvitationEmail(request);

        assertEquals("SENT", result.getStatus());
        assertEquals(
                "invitee@company.com",
                result.getRecipient()
        );
        assertEquals(
                InvitationEmailService.SAFE_HISTORY_MESSAGE,
                result.getMessage()
        );
        assertEquals(
                DeliveryMode.SYNCHRONOUS,
                result.getDeliveryMode()
        );
        assertEquals(0, result.getAttemptCount());
        assertNotNull(result.getSentAt());
        assertEquals(
                List.of("PENDING", "SENT"),
                persistedStatuses
        );

        assertFalse(result.getMessage().contains(RAW_TOKEN));
        assertFalse(result.getMessage().contains(ACCEPTANCE_URL));

        ArgumentCaptor<SimpleMailMessage> mailCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(mailCaptor.capture());

        String renderedEmail = mailCaptor.getValue().getText();

        assertNotNull(renderedEmail);
        assertTrue(renderedEmail.contains(ACCEPTANCE_URL));
        assertTrue(renderedEmail.contains("DEVELOPER"));
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        verify(notificationRepository, times(2))
                .save(any(Notification.class));
    }

    @Test
    void smtpFailureIsRecordedAndPropagatedWithoutRetry() {
        doThrow(new RuntimeException("SMTP provider internal response"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        InvitationDeliveryException exception = assertThrows(
                InvitationDeliveryException.class,
                () -> invitationEmailService.sendInvitationEmail(request)
        );

        assertEquals(
                "Invitation email delivery failed",
                exception.getMessage()
        );
        assertEquals(
                List.of("PENDING", "FAILED"),
                persistedStatuses
        );

        ArgumentCaptor<Notification> notificationCaptor =
                ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2))
                .save(notificationCaptor.capture());

        Notification failed = notificationCaptor
                .getAllValues()
                .get(1);

        assertEquals("FAILED", failed.getStatus());
        assertNull(failed.getSentAt());
        assertEquals(
                InvitationEmailService.SAFE_HISTORY_MESSAGE,
                failed.getMessage()
        );
        assertEquals(
                DeliveryMode.SYNCHRONOUS,
                failed.getDeliveryMode()
        );
        assertEquals(0, failed.getAttemptCount());
        assertFalse(failed.getMessage().contains(RAW_TOKEN));
        assertFalse(failed.getMessage().contains(ACCEPTANCE_URL));
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        verifyNoMoreInteractions(mailSender);
    }

    @Test
    void deliveryExceptionContainsNoInvitationOrSmtpDetails() {
        String providerMessage = "provider rejected secret payload";
        doThrow(new RuntimeException(providerMessage))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        InvitationDeliveryException exception = assertThrows(
                InvitationDeliveryException.class,
                () -> invitationEmailService.sendInvitationEmail(request)
        );

        assertFalse(exception.getMessage().contains(RAW_TOKEN));
        assertFalse(exception.getMessage().contains(ACCEPTANCE_URL));
        assertFalse(exception.getMessage().contains("invitee@company.com"));
        assertFalse(exception.getMessage().contains(providerMessage));
        assertNull(exception.getCause());
    }
}
