package com.featureflag.flag_service.security;

import com.featureflag.flag_service.observability.FlagMetrics;
import com.featureflag.flag_service.service.SdkKeyAuthenticationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

final class SdkKeyAuthenticationFilter extends OncePerRequestFilter {

    static final String HEADER_NAME = "X-Feature-Flag-Key";

    private final SdkKeyAuthenticationService authenticationService;
    private final FlagMetrics flagMetrics;

    SdkKeyAuthenticationFilter(
            SdkKeyAuthenticationService authenticationService,
            FlagMetrics flagMetrics
    ) {
        this.authenticationService = authenticationService;
        this.flagMetrics = flagMetrics;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        List<String> credentials = Collections.list(
                request.getHeaders(HEADER_NAME)
        );
        if (credentials.size() != 1) {
            flagMetrics.sdkAuthenticationFailure("missing");
            unauthorized(response);
            return;
        }

        var principal = authenticationService.authenticate(
                credentials.getFirst()
        );
        if (principal.isEmpty()) {
            flagMetrics.sdkAuthenticationFailure("invalid");
            unauthorized(response);
            return;
        }
        authenticate(principal.orElseThrow(), request);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(
            SdkKeyPrincipal principal,
            HttpServletRequest request
    ) {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_SDK"))
                );
        authentication.setDetails(
                new WebAuthenticationDetailsSource()
                        .buildDetails(request)
        );
        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }

    private void unauthorized(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        try {
            response.getWriter().write(
                    "{\"error\":\"Unauthorized\"}"
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to write authentication response",
                    exception
            );
        }
    }
}
