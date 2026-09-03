package com.featureflag.notification_service.service;

import com.featureflag.notification_service.client.AuthRecipientsClient;
import com.featureflag.notification_service.dto.NotificationRequest;
import com.featureflag.notification_service.entity.DeliveryMode;
import com.featureflag.notification_service.entity.Notification;
import com.featureflag.notification_service.exception.NotificationConflictException;
import com.featureflag.notification_service.exception.ResourceNotFoundException;
import com.featureflag.notification_service.exception.UnsupportedNotificationChannelException;
import com.featureflag.notification_service.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-09-02T12:00:00Z");

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private AuthRecipientsClient authRecipientsClient;

    private NotificationService notificationService;

    private Notification testNotification;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                mailSender,
                authRecipientsClient,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        testNotification = Notification.builder()
                .id(1L)
                .recipient("owner@company.com")
                .creatorEmail("admin@company.com")
                .subject("Feature Flag Created")
                .message("NEW_CHECKOUT has been created")
                .type("EMAIL")
                .status("SENT")
                .build();
    }

    @Test
    @DisplayName("Get Notifications For OWNER - Returns all organization notification activity")
    void testGetNotificationsForUser_Owner() {
        when(notificationRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(testNotification));

        List<Notification> results = notificationService.getNotificationsForUser("owner@company.com", "OWNER");

        assertNotNull(results);
        assertEquals(1, results.size());
        verify(notificationRepository, times(1)).findAllByOrderByCreatedAtDesc();
    }

    @Test
    @DisplayName("Get Notifications For ADMIN - Returns notifications where recipient or creator is the admin")
    void testGetNotificationsForUser_Admin() {
        when(notificationRepository.findByRecipientIgnoreCaseOrCreatorEmailIgnoreCaseOrderByCreatedAtDesc("admina@company.com", "admina@company.com"))
                .thenReturn(List.of(testNotification));

        List<Notification> results = notificationService.getNotificationsForUser("admina@company.com", "ADMIN");

        assertNotNull(results);
        assertEquals(1, results.size());
        verify(notificationRepository, times(1))
                .findByRecipientIgnoreCaseOrCreatorEmailIgnoreCaseOrderByCreatedAtDesc("admina@company.com", "admina@company.com");
    }

    @Test
    @DisplayName("Get Notifications For DEVELOPER - Returns only notifications directed specifically to themselves")
    void testGetNotificationsForUser_Developer() {
        when(notificationRepository.findByRecipientIgnoreCaseOrderByCreatedAtDesc("dev@company.com"))
                .thenReturn(List.of(testNotification));

        List<Notification> results = notificationService.getNotificationsForUser("dev@company.com", "DEVELOPER");

        assertNotNull(results);
        assertEquals(1, results.size());
        verify(notificationRepository, times(1)).findByRecipientIgnoreCaseOrderByCreatedAtDesc("dev@company.com");
    }

    @Test
    @DisplayName("Get User Notifications - Null or blank email returns empty list")
    void testGetUserNotifications_NullOrBlank() {
        List<Notification> nullResults = notificationService.getUserNotifications(null);
        List<Notification> blankResults = notificationService.getUserNotifications("   ");

        assertNotNull(nullResults);
        assertTrue(nullResults.isEmpty());
        assertNotNull(blankResults);
        assertTrue(blankResults.isEmpty());
        verify(notificationRepository, never()).findByRecipientIgnoreCaseOrderByCreatedAtDesc(anyString());
    }

    @Test
    @DisplayName("Create Notification - Sends email and updates status to SENT")
    void testCreateNotification_Success() {
        NotificationRequest request = new NotificationRequest();
        request.setRecipient("admin@company.com");
        request.setCreatorEmail("owner@company.com");
        request.setSubject("Alert");
        request.setMessage("Flag updated");
        request.setType("EMAIL");

        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> {
            Notification n = i.getArgument(0);
            if (n.getId() == null) n.setId(10L);
            return n;
        });

        Notification result = notificationService.createNotification(request);

        assertNotNull(result);
        assertEquals("SENT", result.getStatus());
        assertEquals("owner@company.com", result.getCreatorEmail());
        assertEquals(DeliveryMode.SYNCHRONOUS, result.getDeliveryMode());
        assertEquals(0, result.getAttemptCount());
        assertNotNull(result.getSentAt());
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Create Notification - Mail exception sets status to FAILED")
    void testCreateNotification_MailFailure() {
        NotificationRequest request = new NotificationRequest();
        request.setRecipient("invalid@company.com");
        request.setSubject("Alert");
        request.setMessage("Test message");

        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("SMTP connection failed")).when(mailSender).send(any(SimpleMailMessage.class));

        Notification result = notificationService.createNotification(request);

        assertNotNull(result);
        assertEquals("FAILED", result.getStatus());
        assertEquals(DeliveryMode.SYNCHRONOUS, result.getDeliveryMode());
        assertEquals(0, result.getAttemptCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SMS",
            "PUSH",
            "FAX",
            "email",
            "Email",
            "",
            "   "
    })
    @DisplayName("Create Notification - Unsupported explicit type has no side effects")
    void testCreateNotification_UnsupportedTypeRejectedBeforeSideEffects(
            String type
    ) {
        NotificationRequest request = new NotificationRequest();
        request.setRecipient("admin@company.com");
        request.setSubject("Alert");
        request.setMessage("Message");
        request.setType(type);

        assertThrows(
                UnsupportedNotificationChannelException.class,
                () -> notificationService.createNotification(request)
        );

        verifyNoInteractions(
                notificationRepository,
                authRecipientsClient,
                mailSender
        );
    }

    @Test
    @DisplayName("Create Notification - Null internal type preserves EMAIL default")
    void testCreateNotification_NullInternalTypeDefaultsToEmail() {
        NotificationRequest request = new NotificationRequest();
        request.setRecipient("admin@company.com");
        request.setSubject("Alert");
        request.setMessage("Message");
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Notification result =
                notificationService.createNotification(request);

        assertEquals("EMAIL", result.getType());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Send To Role Recipients - Dispatches email to each resolved recipient")
    void testSendToRoleRecipients_Success() {
        when(authRecipientsClient.getNotificationRecipients(List.of("OWNER", "ADMIN")))
                .thenReturn(List.of("owner@company.com", "admin@company.com"));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        List<Notification> dispatched = notificationService.sendToRoleRecipients(
                "Flag Deleted",
                "Flag was removed",
                "EMAIL",
                List.of("OWNER", "ADMIN")
        );

        assertNotNull(dispatched);
        assertEquals(2, dispatched.size());
        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SMS",
            "PUSH",
            "FAX",
            "email",
            "Email",
            "",
            "   "
    })
    @DisplayName("Send To Role Recipients - Unsupported type is rejected before lookup")
    void testSendToRoleRecipients_UnsupportedTypeRejectedBeforeSideEffects(
            String type
    ) {
        assertThrows(
                UnsupportedNotificationChannelException.class,
                () -> notificationService.sendToRoleRecipients(
                        "Flag changed",
                        "Message",
                        type,
                        List.of("OWNER", "ADMIN")
                )
        );

        verifyNoInteractions(
                notificationRepository,
                authRecipientsClient,
                mailSender
        );
    }

    @Test
    @DisplayName("Send To Role Recipients - Null internal type preserves EMAIL default")
    void testSendToRoleRecipients_NullInternalTypeDefaultsToEmail() {
        when(authRecipientsClient.getNotificationRecipients(
                List.of("OWNER")
        )).thenReturn(List.of("owner@company.com"));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<Notification> notifications =
                notificationService.sendToRoleRecipients(
                        "Flag changed",
                        "Message",
                        null,
                        List.of("OWNER")
                );

        assertEquals("EMAIL", notifications.getFirst().getType());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Send To Role Recipients - Auth lookup failure propagates for Kafka retry")
    void testSendToRoleRecipients_AuthLookupFailurePropagates() {
        RuntimeException authFailure =
                new RuntimeException(
                        "auth service unavailable"
                );
        when(
                authRecipientsClient
                        .getNotificationRecipients(
                                List.of(
                                        "OWNER",
                                        "ADMIN"
                                )
                        )
        ).thenThrow(authFailure);
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> notificationService
                                .sendToRoleRecipients(
                                        "Flag Updated",
                                        "Flag changed",
                                        "EMAIL",
                                        List.of(
                                                "OWNER",
                                                "ADMIN"
                                        )
                                )
                );
        assertSame(
                authFailure,
                exception.getCause()
        );
        verify(
                mailSender,
                never()
        ).send(
                any(SimpleMailMessage.class)
        );
        verify(
                notificationRepository,
                never()
        ).save(
                any(Notification.class)
        );
    }

    @Test
    @DisplayName("Send To Role Recipients - Empty recipient list skips mail delivery")
    void testSendToRoleRecipients_EmptyList() {
        when(authRecipientsClient.getNotificationRecipients(anyList())).thenReturn(List.of());

        List<Notification> dispatched = notificationService.sendToRoleRecipients(
                "Flag Created",
                "Message",
                "EMAIL",
                List.of("OWNER")
        );

        assertNotNull(dispatched);
        assertTrue(dispatched.isEmpty());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Resolve Role Recipients - Normalizes without saving or sending")
    void testResolveRoleRecipientEmails_NormalizesWithoutDelivery() {
        when(
                authRecipientsClient.getNotificationRecipients(
                        List.of("OWNER", "ADMIN")
                )
        ).thenReturn(
                java.util.Arrays.asList(
                        " owner@company.com ",
                        null,
                        "   ",
                        "owner@company.com",
                        "admin@company.com"
                )
        );

        List<String> recipients =
                notificationService.resolveRoleRecipientEmails(
                        List.of("OWNER", "ADMIN")
                );

        assertEquals(
                List.of(
                        "owner@company.com",
                        "admin@company.com"
                ),
                recipients
        );
        verifyNoInteractions(
                notificationRepository,
                mailSender
        );
    }

    @Test
    @DisplayName("Get Notification By ID - Success")
    void testGetNotificationById_Success() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(testNotification));

        Notification found = notificationService.getNotificationById(
                1L,
                "any-owner@company.com",
                "OWNER"
        );

        assertNotNull(found);
        assertEquals("owner@company.com", found.getRecipient());
    }

    @Test
    @DisplayName("Get Notification By ID - Not Found Throws ResourceNotFoundException")
    void testGetNotificationById_NotFound() {
        when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                notificationService.getNotificationById(
                        999L,
                        "owner@company.com",
                        "OWNER"
                )
        );
    }

    @Test
    @DisplayName("Delete Notification - Success")
    void testDeleteNotification_Success() {
        when(notificationRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(testNotification));

        notificationService.deleteNotification(
                1L,
                "ADMIN@company.com ",
                "ADMIN"
        );

        verify(notificationRepository, times(1)).delete(testNotification);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "RETRY", "PROCESSING"})
    @DisplayName("Delete Notification - Active durable delivery is rejected")
    void testDeleteNotification_ActiveDurableRejected(
            String status
    ) {
        testNotification.setDeliveryMode(DeliveryMode.DURABLE);
        testNotification.setStatus(status);
        when(notificationRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(testNotification));

        assertThrows(
                NotificationConflictException.class,
                () -> notificationService.deleteNotification(
                        1L,
                        "admin@company.com",
                        "ADMIN"
                )
        );

        verify(notificationRepository, never())
                .delete(any(Notification.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SENT", "DEAD"})
    @DisplayName("Delete Notification - Terminal durable delivery is allowed")
    void testDeleteNotification_TerminalDurableAllowed(
            String status
    ) {
        testNotification.setDeliveryMode(DeliveryMode.DURABLE);
        testNotification.setStatus(status);
        when(notificationRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(testNotification));

        notificationService.deleteNotification(
                1L,
                "admin@company.com",
                "ADMIN"
        );

        verify(notificationRepository).delete(testNotification);
    }

    @Test
    @DisplayName("Delete Notification - Synchronous and legacy rows preserve existing behavior")
    void testDeleteNotification_SynchronousAndLegacyAllowed() {
        Notification synchronous = testNotification;
        Notification legacy = Notification.builder()
                .id(2L)
                .recipient("admin@company.com")
                .subject("Legacy")
                .message("Legacy")
                .type("EMAIL")
                .status("PENDING")
                .deliveryMode(null)
                .build();
        synchronous.setStatus("PENDING");
        when(notificationRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(synchronous));
        when(notificationRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(legacy));

        notificationService.deleteNotification(
                1L,
                "admin@company.com",
                "ADMIN"
        );
        notificationService.deleteNotification(
                2L,
                "admin@company.com",
                "ADMIN"
        );

        verify(notificationRepository).delete(synchronous);
        verify(notificationRepository).delete(legacy);
    }

    @Test
    @DisplayName("REST create ignores forged creator email")
    void testCreateNotification_RestCreatorCannotBeForged() {
        NotificationRequest request = new NotificationRequest();
        request.setRecipient("recipient@company.com");
        request.setCreatorEmail("forged@company.com");
        request.setSubject("Alert");
        request.setMessage("Message");
        request.setType("EMAIL");

        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        Notification result = notificationService.createNotification(
                request,
                " authenticated-admin@company.com "
        );

        assertEquals("authenticated-admin@company.com", result.getCreatorEmail());
        assertNotEquals("forged@company.com", result.getCreatorEmail());
    }

    @Test
    @DisplayName("ADMIN can read notification when creator email matches ignoring case and whitespace")
    void testGetNotificationById_AdminCreatorAccess() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(testNotification));

        Notification result = notificationService.getNotificationById(
                1L,
                " ADMIN@COMPANY.COM ",
                "ADMIN"
        );

        assertSame(testNotification, result);
    }

    @Test
    @DisplayName("ADMIN can read notification when recipient email matches")
    void testGetNotificationById_AdminRecipientAccess() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(testNotification));

        Notification result = notificationService.getNotificationById(
                1L,
                "owner@company.com",
                "ADMIN"
        );

        assertSame(testNotification, result);
    }

    @Test
    @DisplayName("ADMIN unrelated notification is hidden as not found")
    void testGetNotificationById_AdminUnrelatedDeniedAsNotFound() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(testNotification));

        assertThrows(ResourceNotFoundException.class, () ->
                notificationService.getNotificationById(
                        1L,
                        "unrelated-admin@company.com",
                        "ADMIN"
                )
        );
    }

    @Test
    @DisplayName("DEVELOPER can read own recipient notification")
    void testGetNotificationById_DeveloperRecipientAccess() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(testNotification));

        Notification result = notificationService.getNotificationById(
                1L,
                " OWNER@COMPANY.COM ",
                "DEVELOPER"
        );

        assertSame(testNotification, result);
    }

    @Test
    @DisplayName("DEVELOPER cross-user notification is hidden as not found")
    void testGetNotificationById_DeveloperCrossUserDeniedAsNotFound() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(testNotification));

        assertThrows(ResourceNotFoundException.class, () ->
                notificationService.getNotificationById(
                        1L,
                        "developer@company.com",
                        "DEVELOPER"
                )
        );
    }

    @Test
    @DisplayName("VIEWER cross-user notification is hidden as not found")
    void testGetNotificationById_ViewerCrossUserDeniedAsNotFound() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(testNotification));

        assertThrows(ResourceNotFoundException.class, () ->
                notificationService.getNotificationById(
                        1L,
                        "viewer@company.com",
                        "VIEWER"
                )
        );
    }

    @Test
    @DisplayName("VIEWER can read own recipient notification")
    void testGetNotificationById_ViewerRecipientAccess() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(testNotification));

        Notification result = notificationService.getNotificationById(
                1L,
                "owner@company.com",
                "VIEWER"
        );

        assertSame(testNotification, result);
    }

    @Test
    @DisplayName("Recipient query allows self and rejects another recipient")
    void testRecipientQuery_SelfOnlyForNonOwner() {
        when(notificationRepository.findByRecipientIgnoreCaseOrderByCreatedAtDesc("admin@company.com"))
                .thenReturn(List.of(testNotification));

        List<Notification> ownResults = notificationService.getNotificationsByRecipient(
                " Admin@Company.com ",
                "admin@company.com",
                "ADMIN"
        );

        assertEquals(1, ownResults.size());
        assertThrows(com.featureflag.notification_service.exception.ForbiddenException.class, () ->
                notificationService.getNotificationsByRecipient(
                        "other@company.com",
                        "admin@company.com",
                        "ADMIN"
                )
        );
    }

    @Test
    @DisplayName("ADMIN cannot delete unrelated notification")
    void testDeleteNotification_AdminUnrelatedDeniedAsNotFound() {
        when(notificationRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(testNotification));

        assertThrows(ResourceNotFoundException.class, () ->
                notificationService.deleteNotification(
                        1L,
                        "unrelated-admin@company.com",
                        "ADMIN"
                )
        );

        verify(notificationRepository, never()).delete(any(Notification.class));
    }
}
