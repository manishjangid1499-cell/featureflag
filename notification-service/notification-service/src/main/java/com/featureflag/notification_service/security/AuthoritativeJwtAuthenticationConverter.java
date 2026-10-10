package com.featureflag.notification_service.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class AuthoritativeJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "OWNER", "ADMIN", "DEVELOPER", "VIEWER"
    );

    private final AuthAccountStatusClient accountStatusClient;

    public AuthoritativeJwtAuthenticationConverter(AuthAccountStatusClient accountStatusClient) {
        this.accountStatusClient = accountStatusClient;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String email = jwt.getSubject();
        if (email == null || email.isBlank()) {
            throw new BadCredentialsException("JWT subject is missing or blank");
        }

        Long userId = extractUserId(jwt);
        if (userId == null || userId <= 0) {
            throw new BadCredentialsException("JWT is missing valid numeric userId claim");
        }

        Long tokenVersion = extractTokenVersion(jwt);
        if (tokenVersion == null || tokenVersion <= 0) {
            throw new BadCredentialsException("JWT is missing valid tokenVersion claim");
        }

        Optional<UserAccountStatus> statusOpt = accountStatusClient.getAccountStatus(email, userId);
        if (statusOpt.isEmpty()) {
            throw new BadCredentialsException("User account does not exist or has been deleted");
        }

        UserAccountStatus status = statusOpt.get();
        if (status.userId() == null || !status.userId().equals(userId)) {
            throw new BadCredentialsException("User identity mismatch");
        }

        if (status.email() == null || !status.email().equalsIgnoreCase(email.trim())) {
            throw new BadCredentialsException("User identity email mismatch");
        }

        if (!status.enabled()) {
            throw new BadCredentialsException("User account is disabled");
        }

        if (status.credentialVersion() == null || !status.credentialVersion().equals(tokenVersion)) {
            throw new BadCredentialsException("Token credential version is invalid or stale");
        }

        String rawRole = status.role();
        if (rawRole == null || rawRole.isBlank()) {
            throw new BadCredentialsException("User account has no assigned role");
        }

        String role = rawRole.startsWith("ROLE_") ? rawRole.substring(5) : rawRole;
        if (!ALLOWED_ROLES.contains(role)) {
            throw new BadCredentialsException("Unsupported account role: " + role);
        }

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        return new JwtAuthenticationToken(jwt, authorities, email);
    }

    private static Long extractUserId(Jwt jwt) {
        Object userIdClaim = jwt.getClaim("userId");
        if (userIdClaim == null) {
            return null;
        }
        if (userIdClaim instanceof Number number && number.longValue() > 0) {
            return number.longValue();
        }
        if (userIdClaim instanceof String str && !str.isBlank()) {
            try {
                long val = Long.parseLong(str.trim());
                if (val > 0) {
                    return val;
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Long extractTokenVersion(Jwt jwt) {
        Object versionClaim = jwt.getClaim("tokenVersion");
        if (versionClaim == null) {
            versionClaim = jwt.getClaim("credentialVersion");
        }
        if (versionClaim instanceof Number number && number.longValue() > 0) {
            return number.longValue();
        }
        if (versionClaim instanceof String str && !str.isBlank()) {
            try {
                long val = Long.parseLong(str.trim());
                if (val > 0) {
                    return val;
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
