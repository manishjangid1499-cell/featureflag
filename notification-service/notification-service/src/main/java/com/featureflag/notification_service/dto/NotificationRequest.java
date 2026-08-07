package com.featureflag.notification_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {

    @NotBlank
    @Email
    private String recipient;

    @NotBlank
    private String subject;

    @NotBlank
    private String message;

    @NotBlank
    private String type;
}