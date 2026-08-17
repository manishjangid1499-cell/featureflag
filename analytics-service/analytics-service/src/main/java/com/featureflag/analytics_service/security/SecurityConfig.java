package com.featureflag.analytics_service.security;

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
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()


                        // =========================
                        // DELETE ANALYTICS
                        // OWNER + ADMIN
                        // =========================

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/analytics/**"
                        ).hasAnyRole(
                                "OWNER",
                                "ADMIN"
                        )


                        // =========================
                        // READ ANALYTICS
                        // ALL ROLES
                        // =========================

                        .requestMatchers(
                                HttpMethod.GET,
                                "/analytics/**"
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