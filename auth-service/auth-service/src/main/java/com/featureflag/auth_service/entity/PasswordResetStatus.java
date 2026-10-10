package com.featureflag.auth_service.entity;

public enum PasswordResetStatus {
    PENDING,
    USED,
    EXPIRED,
    REVOKED
}
