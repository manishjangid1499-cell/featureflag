package com.featureflag.notification_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class InvitationEmailRequest {

    @NotBlank(message = "Recipient is required")
    @Email(message = "Recipient must be a valid email address")
    @Size(max = 255)
    private String recipient;

    @Size(max = 255)
    private String inviteeName;

    @NotBlank(message = "Inviter name is required")
    @Size(max = 255)
    private String inviterName;

    @NotBlank(message = "Inviter email is required")
    @Email(message = "Inviter email must be valid")
    @Size(max = 255)
    private String inviterEmail;

    @NotBlank(message = "Role is required")
    @Pattern(
            regexp = "ADMIN|DEVELOPER|VIEWER",
            message = "Role must be ADMIN, DEVELOPER, or VIEWER"
    )
    private String role;

    @Positive(message = "Expiration hours must be positive")
    private int expirationHours;

    @NotBlank(message = "Acceptance URL is required")
    @Size(max = 2048)
    private String acceptanceUrl;
}
