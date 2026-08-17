package com.featureflag.notification_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {

    @NotBlank(message = "Recipient is required")
    @Email(message = "Recipient must be a valid email address")
    private String recipient;

    private String creatorEmail;

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Message is required")
    private String message;

    @NotBlank(message = "Notification type is required")
    @Pattern(
            regexp = "EMAIL|SMS|PUSH",
            message = "Notification type must be EMAIL, SMS, or PUSH"
    )
    private String type;

    public NotificationRequest(String recipient, String subject, String message, String type) {
        this.recipient = recipient;
        this.subject = subject;
        this.message = message;
        this.type = type;
    }
}