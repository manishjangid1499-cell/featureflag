package com.featureflag.auth_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.featureflag.auth_service.entity.InvitationStatus;
import com.featureflag.auth_service.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

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
    private Instant expiresAt;
    private Instant createdAt;
    private Instant acceptedAt;

    // Present only on create/resend. SMTP acceptance is not mailbox receipt.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean emailDeliveryConfirmed;
}
