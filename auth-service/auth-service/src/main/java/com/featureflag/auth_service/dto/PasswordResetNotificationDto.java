package com.featureflag.auth_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetNotificationDto {

    private String recipient;
    private String userName;
    private int expirationMinutes;
    private String resetUrl;
}
