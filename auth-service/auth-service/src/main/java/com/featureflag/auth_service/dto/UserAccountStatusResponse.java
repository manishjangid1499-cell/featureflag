package com.featureflag.auth_service.dto;

import com.featureflag.auth_service.entity.Role;

public record UserAccountStatusResponse(
        Long userId,
        String email,
        Role role,
        boolean enabled,
        Long credentialVersion
) {
    public UserAccountStatusResponse(Long userId, String email, Role role, boolean enabled) {
        this(userId, email, role, enabled, 1L);
    }
}
