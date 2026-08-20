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
public class InvitationNotificationDto {

    private String recipient;
    private String inviteeName;
    private String inviterName;
    private String inviterEmail;
    private String role;
    private int expirationHours;
    private String acceptanceUrl;
}
