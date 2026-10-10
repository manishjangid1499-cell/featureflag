package com.featureflag.flag_service.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * Enforces environment-aware permissions for flag configuration mutations.
 *
 * Policy:
 * - OWNER and ADMIN may mutate flags across all recognized environments (DEV, QA, STAGING, PROD).
 * - DEVELOPER may mutate flags in DEV, QA, and STAGING. PROD requires OWNER or ADMIN.
 * - VIEWER (and any other unpermitted role) cannot mutate flags in any environment.
 * - Invalid or unrecognized environments are rejected; unauthenticated requests are denied.
 */
@Service
public class FlagPermissionService {

    private static final Set<String> RECOGNIZED_ENVIRONMENTS = Set.of("DEV", "QA", "STAGING", "PROD");
    private static final Set<String> DEVELOPER_ALLOWED_ENVIRONMENTS = Set.of("DEV", "QA", "STAGING");

    public void validateMutationPermission(String environment) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        validateMutationPermission(environment, authentication);
    }

    public void validateMutationPermission(String environment, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated");
        }

        if (environment == null || environment.isBlank()) {
            throw new AccessDeniedException("Flag environment is missing or invalid");
        }
        String normalizedEnv = environment.trim().toUpperCase(Locale.ROOT);
        if (!RECOGNIZED_ENVIRONMENTS.contains(normalizedEnv)) {
            throw new AccessDeniedException("Unrecognized environment: " + environment);
        }

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (authorities == null || authorities.isEmpty()) {
            throw new AccessDeniedException("User has no assigned authorities");
        }

        boolean isOwner = hasRole(authorities, "ROLE_OWNER");
        boolean isAdmin = hasRole(authorities, "ROLE_ADMIN");
        boolean isDeveloper = hasRole(authorities, "ROLE_DEVELOPER");

        if (isOwner || isAdmin) {
            // OWNER and ADMIN may mutate flags across all recognized environments
            return;
        }

        if (isDeveloper) {
            if (DEVELOPER_ALLOWED_ENVIRONMENTS.contains(normalizedEnv)) {
                return;
            }
            throw new AccessDeniedException("DEVELOPER role is not permitted to mutate flags in environment: " + normalizedEnv);
        }

        throw new AccessDeniedException("Role is not permitted to mutate flags");
    }

    private boolean hasRole(Collection<? extends GrantedAuthority> authorities, String expectedRole) {
        for (GrantedAuthority authority : authorities) {
            if (expectedRole.equalsIgnoreCase(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
