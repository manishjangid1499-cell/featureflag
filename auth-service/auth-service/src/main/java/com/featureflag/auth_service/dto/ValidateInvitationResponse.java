package com.featureflag.auth_service.dto;

import com.featureflag.auth_service.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidateInvitationResponse {

    private boolean valid;
    private String email;
    private String fullName;
    private Role role;
    private String invitedByName;
    private String errorMessage;
}
