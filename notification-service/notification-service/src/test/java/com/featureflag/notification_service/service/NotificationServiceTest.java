package com.featureflag.notification_service.service;

import com.featureflag.notification_service.client.AuthClient;
import com.featureflag.notification_service.dto.NotificationRequest;
import com.featureflag.notification_service.entity.Notification;
import com.featureflag.notification_service.exception.ResourceNotFoundException;
import com.featureflag.notification_service.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private AuthClient authClient;

    @InjectMocks
    private NotificationService notificationService;

    private Notification testNotification;

    @BeforeEach
    void setUp() {
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
        when(notificationRepository.findByRecipientOrCreatorEmailOrderByCreatedAtDesc("admina@company.com", "admina@company.com"))
                .thenReturn(List.of(testNotification));

        List<Notification> results = notificationService.getNotificationsForUser("admina@company.com", "ADMIN");

        assertNotNull(results);
        assertEquals(1, results.size());
        verify(notificationRepository, times(1))
                .findByRecipientOrCreatorEmailOrderByCreatedAtDesc("admina@company.com", "admina@company.com");
    }

    @Test
    @DisplayName("Get Notifications For DEVELOPER - Returns only notifications directed specifically to themselves")
    void testGetNotificationsForUser_Developer() {
        when(notificationRepository.findByRecipientOrderByCreatedAtDesc("dev@company.com"))
                .thenReturn(List.of(testNotification));

        List<Notification> results = notificationService.getNotificationsForUser("dev@company.com", "DEVELOPER");

        assertNotNull(results);
        assertEquals(1, results.size());
        verify(notificationRepository, times(1)).findByRecipientOrderByCreatedAtDesc("dev@company.com");
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
        verify(notificationRepository, never()).findByRecipientOrderByCreatedAtDesc(anyString());
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
    }

    @Test
    @DisplayName("Send To Role Recipients - Dispatches email to each resolved recipient")
    void testSendToRoleRecipients_Success() {
        when(authClient.getNotificationRecipients(List.of("OWNER", "ADMIN")))
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

    @Test
    @DisplayName("Send To Role Recipients - Empty recipient list skips mail delivery")
    void testSendToRoleRecipients_EmptyList() {
        when(authClient.getNotificationRecipients(anyList())).thenReturn(List.of());

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
    @DisplayName("Get Notification By ID - Success")
    void testGetNotificationById_Success() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(testNotification));

        Notification found = notificationService.getNotificationById(1L);

        assertNotNull(found);
        assertEquals("owner@company.com", found.getRecipient());
    }

    @Test
    @DisplayName("Get Notification By ID - Not Found Throws ResourceNotFoundException")
    void testGetNotificationById_NotFound() {
        when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> notificationService.getNotificationById(999L));
    }

    @Test
    @DisplayName("Delete Notification - Success")
    void testDeleteNotification_Success() {
        when(notificationRepository.existsById(1L)).thenReturn(true);

        notificationService.deleteNotification(1L);

        verify(notificationRepository, times(1)).deleteById(1L);
    }
}
