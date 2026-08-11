package com.featureflag.notification_service.service;

import com.featureflag.notification_service.dto.NotificationRequest;
import com.featureflag.notification_service.entity.Notification;
import com.featureflag.notification_service.exception.ResourceNotFoundException;
import com.featureflag.notification_service.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;

    public Notification createNotification(
            NotificationRequest request
    ) {

        Notification notification = Notification.builder()
                .recipient(request.getRecipient())
                .subject(request.getSubject())
                .message(request.getMessage())
                .type(request.getType())
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        notification =
                notificationRepository.save(notification);

        try {

            SimpleMailMessage mailMessage =
                    new SimpleMailMessage();

            mailMessage.setTo(
                    request.getRecipient()
            );

            mailMessage.setSubject(
                    request.getSubject()
            );

            mailMessage.setText(
                    request.getMessage()
            );

            mailSender.send(mailMessage);

            notification.setStatus("SENT");

            notification.setSentAt(
                    LocalDateTime.now()
            );

        } catch (Exception e) {

            notification.setStatus("FAILED");

            System.out.println(
                    "Failed to send email: "
                            + e.getMessage()
            );
        }

        return notificationRepository.save(
                notification
        );
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