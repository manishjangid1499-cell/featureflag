package com.featureflag.notification_service.service;

import com.featureflag.notification_service.dto.NotificationRequest;
import com.featureflag.notification_service.entity.DeliveryMode;
import com.featureflag.notification_service.entity.Notification;
import com.featureflag.notification_service.exception.ResourceNotFoundException;
import com.featureflag.notification_service.exception.ForbiddenException;
import com.featureflag.notification_service.exception.NotificationConflictException;
import com.featureflag.notification_service.observability.NotificationMetrics;
import com.featureflag.notification_service.repository.NotificationRepository;
import com.featureflag.notification_service.validation.NotificationTypePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;
    private final Clock clock;
    private final NotificationMetrics notificationMetrics;
    private final NotificationAccessPolicy accessPolicy;

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
                .creatorEmail(accessPolicy.normalizeNullableEmail(creatorEmail))
                .subject(request.getSubject())
                .message(request.getMessage())
                .type(type)
                .status("PENDING")
                .createdAt(clock.instant())
                .build();

        notification = notificationRepository.save(notification);

        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setTo(request.getRecipient());
            mailMessage.setSubject(request.getSubject());
            mailMessage.setText(request.getMessage());

            mailSender.send(mailMessage);

            notification.setStatus("SENT");
            notification.setSentAt(clock.instant());
            notificationMetrics.deliverySucceeded("synchronous");
            log.info("Email successfully sent; notificationId={}", notification.getId());

        } catch (Exception e) {
            notification.setStatus("FAILED");
            notificationMetrics.deliveryFailed("synchronous");
            log.warn("Email delivery failed; notificationId={} errorType={}", notification.getId(), e.getClass().getSimpleName());
        }

        return notificationRepository.save(notification);
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

    public Page<Notification> getNotificationsForUser(
            String userEmail,
            String userRole,
            Pageable pageable
    ) {
        if (userEmail == null || userEmail.isBlank()) {
            return Page.empty(pageable);
        }

        String normalizedEmail = userEmail.toLowerCase().trim();
        String normalizedRole = userRole != null
                ? userRole.toUpperCase().trim()
                : "VIEWER";

        if ("OWNER".equals(normalizedRole)) {
            return notificationRepository
                    .findAllByOrderByCreatedAtDescIdDesc(pageable);
        }
        if ("ADMIN".equals(normalizedRole)) {
            return notificationRepository
                    .findByRecipientIgnoreCaseOrCreatorEmailIgnoreCaseOrderByCreatedAtDescIdDesc(
                            normalizedEmail,
                            normalizedEmail,
                            pageable
                    );
        }
        return notificationRepository
                .findByRecipientIgnoreCaseOrderByCreatedAtDescIdDesc(
                        normalizedEmail,
                        pageable
                );
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

        if (!accessPolicy.canAccess(notification, userEmail, userRole)) {
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

        String normalizedRole = accessPolicy.normalizeRole(userRole);
        String normalizedRecipient = accessPolicy.normalizeEmail(recipient);

        if (!"OWNER".equals(normalizedRole)
                && !accessPolicy.emailsEqual(normalizedRecipient, userEmail)) {
            throw new ForbiddenException(
                    "You do not have permission to query this recipient"
            );
        }

        return notificationRepository
                .findByRecipientIgnoreCaseOrderByCreatedAtDesc(normalizedRecipient);
    }

    public Page<Notification> getNotificationsByRecipient(
            String recipient,
            String userEmail,
            String userRole,
            Pageable pageable
    ) {
        String normalizedRole = accessPolicy.normalizeRole(userRole);
        String normalizedRecipient = accessPolicy.normalizeEmail(recipient);

        if (!"OWNER".equals(normalizedRole)
                && !accessPolicy.emailsEqual(normalizedRecipient, userEmail)) {
            throw new ForbiddenException(
                    "You do not have permission to query this recipient"
            );
        }

        return notificationRepository
                .findByRecipientIgnoreCaseOrderByCreatedAtDescIdDesc(
                        normalizedRecipient,
                        pageable
                );
    }

    public List<Notification> getNotificationsByStatus(
            String status
    ) {

        return notificationRepository
                .findByStatus(status);
    }

    public Page<Notification> getNotificationsByStatus(
            String status,
            Pageable pageable
    ) {
        return notificationRepository
                .findByStatusOrderByCreatedAtDescIdDesc(status, pageable);
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

        if (!accessPolicy.canAccess(notification, userEmail, userRole)) {
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

}
