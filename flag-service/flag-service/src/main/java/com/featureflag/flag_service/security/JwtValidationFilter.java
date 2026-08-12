package com.featureflag.flag_service.security;

import com.featureflag.flag_service.client.AuthClient;
import com.featureflag.flag_service.dto.TokenValidationResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtValidationFilter extends OncePerRequestFilter {

    private final AuthClient authClient;

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request
    ) {

        String path = request.getServletPath();

        // ==========================================
        // PUBLIC / NON-AUTHENTICATED REQUESTS
        // ==========================================

        if (request.getMethod().equalsIgnoreCase("OPTIONS")) {
            return true;
        }

        return path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader =
                request.getHeader("Authorization");

        // ==========================================
        // NO JWT
        // ==========================================

        if (authHeader == null ||
                !authHeader.startsWith("Bearer ")) {

            response.setStatus(
                    HttpServletResponse.SC_UNAUTHORIZED
            );

            return;
        }

        String token =
                authHeader.substring(7);

        try {

            // ==========================================
            // ASK AUTH-SERVICE TO VALIDATE JWT
            // ==========================================

            TokenValidationResponse validation =
                    authClient.validateToken(token);

            if (validation == null ||
                    !validation.isValid()) {

                SecurityContextHolder.clearContext();

                response.setStatus(
                        HttpServletResponse.SC_UNAUTHORIZED
                );

                return;
            }

            // ==========================================
            // GET ROLE
            // ==========================================

            String role =
                    validation.getRole();

            if (role == null ||
                    role.isBlank()) {

                SecurityContextHolder.clearContext();

                response.setStatus(
                        HttpServletResponse.SC_UNAUTHORIZED
                );

                return;
            }

            // ==========================================
            // CREATE SPRING SECURITY AUTHORITY
            // ==========================================

            SimpleGrantedAuthority authority =
                    new SimpleGrantedAuthority(
                            "ROLE_" + role
                    );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            validation.getEmail(),
                            null,
                            List.of(authority)
                    );

            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);

            // ==========================================
            // CONTINUE REQUEST
            // ==========================================

            filterChain.doFilter(
                    request,
                    response
            );

        } catch (Exception e) {

            e.printStackTrace();

            SecurityContextHolder.clearContext();

            response.setStatus(
                    HttpServletResponse.SC_UNAUTHORIZED
            );
        }
    }
}