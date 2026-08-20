package com.featureflag.notification_service.service;

import com.featureflag.notification_service.dto.InvitationEmailRequest;
import com.featureflag.notification_service.entity.Notification;
import com.featureflag.notification_service.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationEmailService {

    static final String SUBJECT =
            "You've been invited to FeatureFlag Platform";

    /*
     * This value is intentionally safe to persist. The rendered email body and
     * acceptance URL contain a one-time secret and must never be written to DB.
     */
    static final String SAFE_HISTORY_MESSAGE =
            "Member invitation email";

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;

    public Notification sendInvitationEmail(InvitationEmailRequest request) {
        String recipient = normalizeEmail(request.getRecipient());
        String creatorEmail = normalizeEmail(request.getInviterEmail());

        Notification notification = Notification.builder()
                .recipient(recipient)
                .creatorEmail(creatorEmail)
                .subject(SUBJECT)
                .message(SAFE_HISTORY_MESSAGE)
                .type("EMAIL")
                .status("PENDING")
                .createdAt(LocalDateTime.now())
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
            notification.setSentAt(LocalDateTime.now());

            log.info(
                    "Invitation email delivered; notificationId={}",
                    notification.getId()
            );
        } catch (Exception exception) {
            notification.setStatus("FAILED");

            // Do not log recipient, rendered body, acceptance URL, or exception
            // message because those can contain sensitive delivery context.
            log.warn(
                    "Invitation email delivery failed; notificationId={} errorType={}",
                    notification.getId(),
                    exception.getClass().getSimpleName()
            );
        }

        return notificationRepository.save(notification);
    }

    private String buildEmailBody(InvitationEmailRequest request) {
        String inviteeName =
                request.getInviteeName() == null
                        || request.getInviteeName().isBlank()
                        ? "there"
                        : request.getInviteeName().trim();

        return String.format(
                "Hello %s,%n%n"
                        + "%s (%s) has invited you to join the "
                        + "FeatureFlag Platform as a %s.%n%n"
                        + "This invitation will expire in %d hours.%n%n"
                        + "To accept your invitation and set your account "
                        + "password, click the link below:%n"
                        + "%s%n%n"
                        + "If you did not expect this invitation, "
                        + "you can safely ignore this email.",
                inviteeName,
                request.getInviterName().trim(),
                request.getInviterEmail().trim(),
                request.getRole(),
                request.getExpirationHours(),
                request.getAcceptanceUrl()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
