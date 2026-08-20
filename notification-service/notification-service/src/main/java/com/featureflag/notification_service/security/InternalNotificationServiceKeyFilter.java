package com.featureflag.notification_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class InternalNotificationServiceKeyFilter
        extends OncePerRequestFilter {

    public static final String HEADER_NAME =
            "X-Notification-Service-Key";

    public static final String AUTHORITY =
            "NOTIFICATION_INTERNAL_SERVICE";

    public static final String INVITATION_PATH =
            "/internal/notifications/invitations";

    private final String configuredServiceKey;

    public InternalNotificationServiceKeyFilter(
            @Value("${NOTIFICATION_INTERNAL_SERVICE_KEY:}")
            String configuredServiceKey
    ) {
        this.configuredServiceKey = configuredServiceKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !INVITATION_PATH.equals(request.getServletPath())
                && !INVITATION_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String providedServiceKey =
                request.getHeader(HEADER_NAME);

        if (!isValidServiceKey(providedServiceKey)) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "auth-service",
                        null,
                        List.of(
                                new SimpleGrantedAuthority(AUTHORITY)
                        )
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private boolean isValidServiceKey(String providedServiceKey) {
        if (!StringUtils.hasText(configuredServiceKey)
                || !StringUtils.hasText(providedServiceKey)) {
            return false;
        }

        return MessageDigest.isEqual(
                configuredServiceKey.getBytes(StandardCharsets.UTF_8),
                providedServiceKey.getBytes(StandardCharsets.UTF_8)
        );
    }
}
