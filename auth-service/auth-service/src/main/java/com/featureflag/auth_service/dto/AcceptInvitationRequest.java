package com.featureflag.auth_service.dto;

import com.featureflag.auth_service.validation.BcryptPassword;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcceptInvitationRequest {

    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @BcryptPassword
    private String password;

    @NotBlank(message = "Confirm password is required")
    @BcryptPassword
    private String confirmPassword;
}
