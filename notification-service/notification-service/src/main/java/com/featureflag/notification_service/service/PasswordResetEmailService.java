package com.featureflag.notification_service.service;

import com.featureflag.notification_service.dto.PasswordResetEmailRequest;
import com.featureflag.notification_service.entity.Notification;
import com.featureflag.notification_service.exception.InvitationDeliveryException;
import com.featureflag.notification_service.observability.NotificationMetrics;
import com.featureflag.notification_service.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetEmailService {

    static final String SUBJECT =
            "Reset your FeatureFlag Platform password";

    /*
     * This value is intentionally safe to persist. The rendered email body and
     * reset URL contain a one-time secret and must never be written to DB.
     */
    static final String SAFE_HISTORY_MESSAGE =
            "Password reset email";

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;
    private final Clock clock;
    private final NotificationMetrics notificationMetrics;

    public Notification sendPasswordResetEmail(PasswordResetEmailRequest request) {
        String recipient = normalizeEmail(request.getRecipient());

        Notification notification = Notification.builder()
                .recipient(recipient)
                .creatorEmail("system@featureflag.platform")
                .subject(SUBJECT)
                .message(SAFE_HISTORY_MESSAGE)
                .type("EMAIL")
                .status("PENDING")
                .createdAt(clock.instant())
                .build();

        notification = notificationRepository.save(notification);

        String emailBody = buildEmailBody(request);

        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setTo(recipient);
            mailMessage.setSubject(SUBJECT);
            mailMessage.setText(emailBody);

            mailSender.send(mailMessage);

            notification.setStatus("SENT");
            notification.setSentAt(clock.instant());
        } catch (Exception exception) {
            notificationMetrics.deliveryFailed("password_reset");
            notification.setStatus("FAILED");

            notification = notificationRepository.save(notification);

            // Do not log recipient, rendered body, reset URL, or exception
            // message because those can contain sensitive delivery context.
            log.warn(
                    "Password reset email delivery failed; notificationId={} errorType={}",
                    notification.getId(),
                    exception.getClass().getSimpleName()
            );

            throw new InvitationDeliveryException();
        }

        notification = notificationRepository.save(notification);
        notificationMetrics.deliverySucceeded("password_reset");

        log.info(
                "Password reset email delivered; notificationId={}",
                notification.getId()
        );

        return notification;
    }

    private String buildEmailBody(PasswordResetEmailRequest request) {
        String greetingName =
                request.getUserName() == null || request.getUserName().isBlank()
                        ? "there"
                        : request.getUserName().trim();

        return String.format(
                "Hello %s,%n%n"
                        + "A password reset request was received for your FeatureFlag Platform account.%n%n"
                        + "This reset link will expire in %d minutes.%n%n"
                        + "To reset your password, click the link below:%n"
                        + "%s%n%n"
                        + "If you did not request a password reset, you can safely ignore this email.%n"
                        + "Your account password will not change unless you access the link above.",
                greetingName,
                request.getExpirationMinutes(),
                request.getResetUrl()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
