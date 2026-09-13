package com.featureflag.auth_service.config;

import com.featureflag.auth_service.exception.ApiProblemDetails;
import com.featureflag.auth_service.security.JwtAuthenticationFilter;
import com.featureflag.auth_service.security.AuthRecipientsServiceKeyFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthRecipientsServiceKeyFilter authRecipientsServiceKeyFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiProblemDetails problems
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

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                problems.write(request, response, HttpStatus.UNAUTHORIZED,
                                        "unauthenticated", "Unauthorized",
                                        "Authentication is required")
                        )
                        .accessDeniedHandler((request, response, exception) ->
                                problems.write(request, response, HttpStatus.FORBIDDEN,
                                        "forbidden", "Forbidden",
                                        "You do not have permission to access this resource")
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                "/auth/login",
                                "/auth/invitations/**",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()

                        .requestMatchers(
                                "/auth/recipients"
                        ).hasAuthority(
                                AuthRecipientsServiceKeyFilter.AUTHORITY
                        )

                        .requestMatchers(
                                "/members/**"
                        ).hasAnyRole(
                                "OWNER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                "/auth/profile"
                        ).authenticated()

                        .anyRequest()
                        .authenticated()
                )

                .addFilterBefore(
                        authRecipientsServiceKeyFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder();
    }

}
