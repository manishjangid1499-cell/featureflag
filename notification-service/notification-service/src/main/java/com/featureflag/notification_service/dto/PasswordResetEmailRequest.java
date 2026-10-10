package com.featureflag.notification_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetEmailRequest {

    @NotBlank(message = "Recipient is required")
    @Email(message = "Recipient must be a valid email address")
    @Size(max = 255)
    private String recipient;

    @Size(max = 255)
    private String userName;

    @Positive(message = "Expiration minutes must be positive")
    private int expirationMinutes;

    @NotBlank(message = "Reset URL is required")
    @Size(max = 2048)
    private String resetUrl;
}
