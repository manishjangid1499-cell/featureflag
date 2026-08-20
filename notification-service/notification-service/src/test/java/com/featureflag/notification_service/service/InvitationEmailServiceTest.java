package com.featureflag.notification_service.service;

import com.featureflag.notification_service.dto.InvitationEmailRequest;
import com.featureflag.notification_service.entity.Notification;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationEmailServiceTest {

    private static final String RAW_TOKEN =
            "super-secret-invitation-token";

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

    @BeforeEach
    void setUp() {
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

        assertFalse(result.getMessage().contains(RAW_TOKEN));
        assertFalse(result.getMessage().contains(ACCEPTANCE_URL));

        ArgumentCaptor<SimpleMailMessage> mailCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(mailCaptor.capture());

        String renderedEmail = mailCaptor.getValue().getText();

        assertNotNull(renderedEmail);
        assertTrue(renderedEmail.contains(ACCEPTANCE_URL));
        assertTrue(renderedEmail.contains("DEVELOPER"));
    }

    @Test
    void smtpFailureIsRecordedWithoutPersistingSecretBody() {
        doThrow(new RuntimeException("SMTP connection failed"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        Notification result =
                invitationEmailService.sendInvitationEmail(request);

        assertEquals("FAILED", result.getStatus());
        assertEquals(
                InvitationEmailService.SAFE_HISTORY_MESSAGE,
                result.getMessage()
        );
        assertFalse(result.getMessage().contains(RAW_TOKEN));
        assertFalse(result.getMessage().contains(ACCEPTANCE_URL));
    }
}
