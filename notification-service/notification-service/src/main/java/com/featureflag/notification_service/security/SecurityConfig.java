package com.featureflag.notification_service.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtValidationFilter jwtValidationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .csrf(csrf ->
                        csrf.disable()
                )

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        // =========================
                        // PUBLIC
                        // =========================

                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()


                        // =========================
                        // CREATE NOTIFICATION
                        // OWNER + ADMIN
                        // =========================

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/notifications"
                        ).hasAnyRole(
                                "OWNER",
                                "ADMIN"
                        )


                        // =========================
                        // DELETE NOTIFICATION
                        // OWNER + ADMIN
                        // =========================

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/notifications/**"
                        ).hasAnyRole(
                                "OWNER",
                                "ADMIN"
                        )


                        // =========================
                        // READ NOTIFICATIONS
                        // ALL ROLES
                        // =========================

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/notifications/**"
                        ).hasAnyRole(
                                "OWNER",
                                "ADMIN",
                                "DEVELOPER",
                                "VIEWER"
                        )


                        // =========================
                        // EVERYTHING ELSE
                        // =========================

                        .anyRequest()
                        .authenticated()
                )

                .addFilterBefore(
                        jwtValidationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}