package com.featureflag.notification_service.security;

public record UserAccountStatus(
        Long userId,
        String email,
        String role,
        boolean enabled,
        Long credentialVersion
) {
    public UserAccountStatus(Long userId, String email, String role, boolean enabled) {
        this(userId, email, role, enabled, 1L);
    }
}
