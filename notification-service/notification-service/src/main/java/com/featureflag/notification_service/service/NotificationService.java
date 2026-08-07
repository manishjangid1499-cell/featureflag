package com.featureflag.notification_service.service;

import com.featureflag.notification_service.dto.NotificationRequest;
import com.featureflag.notification_service.entity.Notification;
import com.featureflag.notification_service.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;

    public Notification createNotification(NotificationRequest request) {

        Notification notification = Notification.builder()
                .recipient(request.getRecipient())
                .subject(request.getSubject())
                .message(request.getMessage())
                .type(request.getType())
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        // Save notification first
        notification = notificationRepository.save(notification);

        try {
            // Create email
            SimpleMailMessage mailMessage = new SimpleMailMessage();

            mailMessage.setTo(request.getRecipient());
            mailMessage.setSubject(request.getSubject());
            mailMessage.setText(request.getMessage());

            // Send email
            mailSender.send(mailMessage);

            // Update status after successful sending
            notification.setStatus("SENT");
            notification.setSentAt(LocalDateTime.now());

        } catch (Exception e) {

            notification.setStatus("FAILED");

            System.out.println("Failed to send email: " + e.getMessage());
        }

        // Save updated status
        return notificationRepository.save(notification);
    }
}