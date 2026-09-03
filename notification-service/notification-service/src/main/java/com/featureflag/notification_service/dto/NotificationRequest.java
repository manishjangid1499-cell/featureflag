package com.featureflag.notification_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.featureflag.notification_service.validation.NotificationTypePolicy.EMAIL;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {

    @NotBlank(message = "Recipient is required")
    @Email(message = "Recipient must be a valid email address")
    @Size(max = 254, message = "Recipient is too long")
    private String recipient;

    @Email(message = "Creator email must be valid")
    @Size(max = 254, message = "Creator email is too long")
    private String creatorEmail;

    @NotBlank(message = "Subject is required")
    @Size(max = 255, message = "Subject cannot exceed 255 characters")
    private String subject;

    @NotBlank(message = "Message is required")
    @Size(max = 65535, message = "Message cannot exceed 65535 characters")
    private String message;

    @NotBlank(message = "Notification type is required")
    @Pattern(
            regexp = EMAIL,
            message = "Notification type must be EMAIL"
    )
    @Size(max = 20, message = "Notification type is too long")
    private String type;

    public NotificationRequest(String recipient, String subject, String message, String type) {
        this.recipient = recipient;
        this.subject = subject;
        this.message = message;
        this.type = type;
    }
}
