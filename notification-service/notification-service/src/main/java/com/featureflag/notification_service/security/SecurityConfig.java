package com.featureflag.notification_service.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Converter<Jwt, ? extends AbstractAuthenticationToken>
                    jwtAuthenticationConverter,
            InternalNotificationServiceKeyFilter
                    internalNotificationServiceKeyFilter
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
                        // INTERNAL SERVICE-TO-SERVICE
                        // =========================

                        .requestMatchers(
                                HttpMethod.POST,
                                InternalNotificationServiceKeyFilter.INVITATION_PATH
                        ).hasAuthority(
                                InternalNotificationServiceKeyFilter.AUTHORITY
                        )

                        // =========================
                        // PUBLIC
                        // =========================

                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/actuator/health/**"
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

                        // ORGANIZATION-WIDE STATUS QUERY - OWNER ONLY
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/notifications/status/**"
                        ).hasRole(
                                "OWNER"
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

                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        jwtAuthenticationConverter
                                )
                        )
                )

                .addFilterBefore(
                        internalNotificationServiceKeyFilter,
                        BearerTokenAuthenticationFilter.class
                );

        return http.build();
    }
}
