package com.featureflag.notification_service.service;

import com.featureflag.notification_service.dto.InvitationEmailRequest;
import com.featureflag.notification_service.entity.DeliveryMode;
import com.featureflag.notification_service.entity.Notification;
import com.featureflag.notification_service.exception.InvitationDeliveryException;
import com.featureflag.notification_service.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@DataJpaTest
@ActiveProfiles("test")
@Import(InvitationEmailService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InvitationEmailServiceIntegrationTest {

    private static final String RAW_TOKEN =
            "VERY_SECRET_COMMIT_SURVIVAL_TOKEN";

    private static final String ACCEPTANCE_URL =
            "https://frontend.example.test/accept-invitation?token="
                    + RAW_TOKEN;

    @Autowired
    private InvitationEmailService invitationEmailService;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
    }

    @Test
    void failedHistoryCommitsBeforeDeliveryExceptionEscapes() {
        doThrow(new RuntimeException("SMTP provider private response"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        assertThrows(
                InvitationDeliveryException.class,
                () -> invitationEmailService.sendInvitationEmail(request())
        );

        List<Notification> persisted = notificationRepository.findAll();

        assertEquals(1, persisted.size());
        Notification failed = persisted.getFirst();
        assertEquals("FAILED", failed.getStatus());
        assertNull(failed.getSentAt());
        assertEquals(
                InvitationEmailService.SAFE_HISTORY_MESSAGE,
                failed.getMessage()
        );
        assertEquals(DeliveryMode.SYNCHRONOUS, failed.getDeliveryMode());
        assertEquals(0, failed.getAttemptCount());
        assertFalse(failed.getMessage().contains(RAW_TOKEN));
        assertFalse(failed.getMessage().contains(ACCEPTANCE_URL));
    }

    private InvitationEmailRequest request() {
        return new InvitationEmailRequest(
                "invitee@company.com",
                "Invitee",
                "Owner",
                "owner@company.com",
                "DEVELOPER",
                48,
                ACCEPTANCE_URL
        );
    }
}
