package com.featureflag.audit_service.security;

import java.util.Optional;

public interface AuthAccountStatusClient {

    Optional<UserAccountStatus> getAccountStatus(String email, Long userId);
}
