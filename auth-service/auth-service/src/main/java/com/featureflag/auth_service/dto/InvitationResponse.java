package com.featureflag.auth_service.dto;

import com.featureflag.auth_service.entity.InvitationStatus;
import com.featureflag.auth_service.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvitationResponse {

    private Long id;
    private String email;
    private String fullName;
    private Role invitedRole;
    private Long invitedByUserId;
    private String invitedByEmail;
    private String invitedByName;
    private InvitationStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime acceptedAt;
}
