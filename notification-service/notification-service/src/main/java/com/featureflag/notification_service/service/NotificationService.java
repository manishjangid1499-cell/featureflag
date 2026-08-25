package com.featureflag.notification_service.service;

import com.featureflag.notification_service.client.AuthRecipientsClient;
import com.featureflag.notification_service.dto.NotificationRequest;
import com.featureflag.notification_service.entity.DeliveryMode;
import com.featureflag.notification_service.entity.Notification;
import com.featureflag.notification_service.exception.ResourceNotFoundException;
import com.featureflag.notification_service.exception.ForbiddenException;
import com.featureflag.notification_service.exception.NotificationConflictException;
import com.featureflag.notification_service.repository.NotificationRepository;
import com.featureflag.notification_service.validation.NotificationTypePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;
    private final AuthRecipientsClient authRecipientsClient;

    public Notification createNotification(
            NotificationRequest request
    ) {

        return createNotificationInternal(request, request.getCreatorEmail());
    }

    public Notification createNotification(
            NotificationRequest request,
            String authenticatedCreatorEmail
    ) {

        return createNotificationInternal(request, authenticatedCreatorEmail);
    }

    private Notification createNotificationInternal(
            NotificationRequest request,
            String creatorEmail
    ) {
        String type = NotificationTypePolicy.resolveInternalType(
                request.getType()
        );

        Notification notification = Notification.builder()
                .recipient(request.getRecipient().trim())
                .creatorEmail(normalizeNullableEmail(creatorEmail))
                .subject(request.getSubject())
                .message(request.getMessage())
                .type(type)
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
            log.info("Email successfully sent; notificationId={}", notification.getId());

        } catch (Exception e) {
            notification.setStatus("FAILED");
            log.warn("Email delivery failed; notificationId={} errorType={}", notification.getId(), e.getClass().getSimpleName());
        }

        return notificationRepository.save(notification);
    }

    public List<Notification> sendToRoleRecipients(
            String subject,
            String message,
            String type,
            List<String> targetRoles
    ) {
        String resolvedType =
                NotificationTypePolicy.resolveInternalType(type);

        List<String> recipients =
                resolveRoleRecipientEmails(targetRoles);

        if (recipients.isEmpty()) {
            log.warn("No active notification recipients found for configured roles; email dispatch skipped");
            return List.of();
        }

        log.info("Dispatching notification to {} database recipient(s)", recipients.size());

        List<Notification> dispatched = new ArrayList<>();

        for (String recipientEmail : recipients) {
            Notification notification = Notification.builder()
                    .recipient(recipientEmail)
                    .subject(subject)
                    .message(message)
                    .type(resolvedType)
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
                log.info("Email notification sent; notificationId={}", notification.getId());

            } catch (Exception e) {
                notification.setStatus("FAILED");
                log.warn("Email delivery failed; notificationId={} errorType={}", notification.getId(), e.getClass().getSimpleName());
            }

            dispatched.add(notificationRepository.save(notification));
        }

        return dispatched;
    }

    public List<String> resolveRoleRecipientEmails(
            List<String> targetRoles
    ) {
        List<String> recipients = new ArrayList<>();

        try {
            List<String> fetched = authRecipientsClient.getNotificationRecipients(targetRoles);
            if (fetched != null) {
                recipients = fetched.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(email -> !email.isBlank())
                        .distinct()
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.error(
                    "Failed to retrieve notification recipients from Auth Service; errorType={}",
                    e.getClass().getSimpleName()
            );
            throw new IllegalStateException(
                    "Failed to retrieve notification recipients from Auth Service",
                    e
            );
        }

        return recipients;
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
            return notificationRepository
                    .findByRecipientIgnoreCaseOrCreatorEmailIgnoreCaseOrderByCreatedAtDesc(
                            normalizedEmail,
                            normalizedEmail
                    );
        } else {
            // DEVELOPER and VIEWER see only notifications directed specifically to themselves
            return notificationRepository
                    .findByRecipientIgnoreCaseOrderByCreatedAtDesc(normalizedEmail);
        }
    }

    public List<Notification> getUserNotifications(String userEmail) {
        return getNotificationsForUser(userEmail, "VIEWER");
    }

    public List<Notification> getAllNotifications() {

        return notificationRepository.findAll();
    }

    public Notification getNotificationById(
            Long id,
            String userEmail,
            String userRole
    ) {

        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Notification not found with id: "
                                        + id
                        )
                );

        if (!canAccessNotification(notification, userEmail, userRole)) {
            throw new ResourceNotFoundException(
                    "Notification not found with id: " + id
            );
        }

        return notification;
    }

    public List<Notification> getNotificationsByRecipient(
            String recipient,
            String userEmail,
            String userRole
    ) {

        String normalizedRole = normalizeRole(userRole);
        String normalizedRecipient = normalizeEmail(recipient);

        if (!"OWNER".equals(normalizedRole)
                && !emailsEqual(normalizedRecipient, userEmail)) {
            throw new ForbiddenException(
                    "You do not have permission to query this recipient"
            );
        }

        return notificationRepository
                .findByRecipientIgnoreCaseOrderByCreatedAtDesc(normalizedRecipient);
    }

    public List<Notification> getNotificationsByStatus(
            String status
    ) {

        return notificationRepository
                .findByStatus(status);
    }

    @Transactional
    public void deleteNotification(
            Long id,
            String userEmail,
            String userRole
    ) {

        Notification notification = notificationRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification not found with id: " + id
                ));

        if (!canAccessNotification(notification, userEmail, userRole)) {
            throw new ResourceNotFoundException(
                    "Notification not found with id: " + id
            );
        }

        if (isActiveDurableDelivery(notification)) {
            throw new NotificationConflictException(
                    "Active notification delivery cannot be deleted"
            );
        }

        notificationRepository.delete(notification);
    }

    private boolean isActiveDurableDelivery(
            Notification notification
    ) {
        if (notification.getDeliveryMode()
                != DeliveryMode.DURABLE) {
            return false;
        }

        return "PENDING".equals(notification.getStatus())
                || "RETRY".equals(notification.getStatus())
                || "PROCESSING".equals(notification.getStatus());
    }

    private boolean canAccessNotification(
            Notification notification,
            String userEmail,
            String userRole
    ) {
        String normalizedRole = normalizeRole(userRole);

        if ("OWNER".equals(normalizedRole)) {
            return true;
        }

        boolean isRecipient = emailsEqual(notification.getRecipient(), userEmail);

        if ("ADMIN".equals(normalizedRole)) {
            return isRecipient || emailsEqual(notification.getCreatorEmail(), userEmail);
        }

        return isRecipient;
    }

    private boolean emailsEqual(String first, String second) {
        return first != null
                && second != null
                && first.trim().equalsIgnoreCase(second.trim());
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullableEmail(String email) {
        return email == null ? null : email.trim();
    }

    private String normalizeRole(String role) {
        return role == null ? "VIEWER" : role.trim().toUpperCase(Locale.ROOT);
    }
}
