package com.featureflag.notification_service.service;

import com.featureflag.notification_service.client.AuthClient;
import com.featureflag.notification_service.dto.NotificationRequest;
import com.featureflag.notification_service.entity.Notification;
import com.featureflag.notification_service.exception.ResourceNotFoundException;
import com.featureflag.notification_service.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;
    private final AuthClient authClient;

    public Notification createNotification(
            NotificationRequest request
    ) {

        Notification notification = Notification.builder()
                .recipient(request.getRecipient())
                .creatorEmail(request.getCreatorEmail())
                .subject(request.getSubject())
                .message(request.getMessage())
                .type(request.getType() != null ? request.getType() : "EMAIL")
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        notification = notificationRepository.save(notification);

        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setTo(request.getRecipient());
            mailMessage.setSubject(request.getSubject());
            mailMessage.setText(request.getMessage());

            mailSender.send(mailMessage);

            notification.setStatus("SENT");
            notification.setSentAt(LocalDateTime.now());
            log.info("Email successfully sent to {}", request.getRecipient());

        } catch (Exception e) {
            notification.setStatus("FAILED");
            log.warn("Failed to send email to {}: {}", request.getRecipient(), e.getMessage());
        }

        return notificationRepository.save(notification);
    }

    public List<Notification> sendToRoleRecipients(
            String subject,
            String message,
            String type,
            List<String> targetRoles
    ) {
        List<String> recipients = new ArrayList<>();

        try {
            List<String> fetched = authClient.getNotificationRecipients(targetRoles);
            if (fetched != null) {
                recipients = fetched.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(email -> !email.isBlank())
                        .distinct()
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.error("Failed to retrieve notification recipients from Auth Service: {}", e.getMessage());
        }

        if (recipients.isEmpty()) {
            log.warn("No active OWNER/ADMIN recipients found in database for notification: '{}'. Skipping email dispatch.", subject);
            return List.of();
        }

        log.info("Dispatching notification '{}' to {} database recipient(s): {}", subject, recipients.size(), recipients);

        List<Notification> dispatched = new ArrayList<>();

        for (String recipientEmail : recipients) {
            Notification notification = Notification.builder()
                    .recipient(recipientEmail)
                    .subject(subject)
                    .message(message)
                    .type(type != null ? type : "EMAIL")
                    .status("PENDING")
                    .createdAt(LocalDateTime.now())
                    .build();

            notification = notificationRepository.save(notification);

            try {
                SimpleMailMessage mailMessage = new SimpleMailMessage();
                mailMessage.setTo(recipientEmail);
                mailMessage.setSubject(subject);
                mailMessage.setText(message);

                mailSender.send(mailMessage);

                notification.setStatus("SENT");
                notification.setSentAt(LocalDateTime.now());
                log.info("Email notification sent to database recipient: {}", recipientEmail);

            } catch (Exception e) {
                notification.setStatus("FAILED");
                log.warn("Email delivery failed for recipient {}: {}", recipientEmail, e.getMessage());
            }

            dispatched.add(notificationRepository.save(notification));
        }

        return dispatched;
    }

    public List<Notification> getNotificationsForUser(String userEmail, String userRole) {
        if (userEmail == null || userEmail.isBlank()) {
            return List.of();
        }

        String normalizedEmail = userEmail.toLowerCase().trim();
        String normalizedRole = userRole != null ? userRole.toUpperCase().trim() : "VIEWER";

        if ("OWNER".equals(normalizedRole)) {
            // OWNER sees all organization invitation & notification activity
            return notificationRepository.findAllByOrderByCreatedAtDesc();
        } else if ("ADMIN".equals(normalizedRole)) {
            // ADMIN sees notifications where they are the recipient OR the creator/actor of the action
            return notificationRepository.findByRecipientOrCreatorEmailOrderByCreatedAtDesc(normalizedEmail, normalizedEmail);
        } else {
            // DEVELOPER and VIEWER see only notifications directed specifically to themselves
            return notificationRepository.findByRecipientOrderByCreatedAtDesc(normalizedEmail);
        }
    }

    public List<Notification> getUserNotifications(String userEmail) {
        return getNotificationsForUser(userEmail, "VIEWER");
    }

    public List<Notification> getAllNotifications() {

        return notificationRepository.findAll();
    }

    public Notification getNotificationById(Long id) {

        return notificationRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Notification not found with id: "
                                        + id
                        )
                );
    }

    public List<Notification> getNotificationsByRecipient(
            String recipient
    ) {

        return notificationRepository
                .findByRecipient(recipient);
    }

    public List<Notification> getNotificationsByStatus(
            String status
    ) {

        return notificationRepository
                .findByStatus(status);
    }

    public void deleteNotification(Long id) {

        if (!notificationRepository.existsById(id)) {

            throw new ResourceNotFoundException(
                    "Notification not found with id: "
                            + id
            );
        }

        notificationRepository.deleteById(id);
    }
}