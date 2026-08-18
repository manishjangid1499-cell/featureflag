package com.featureflag.auth_service.config;

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
            HttpSecurity http
    ) throws Exception {

        http

                // =========================
                // CSRF
                // =========================

                .csrf(csrf ->
                        csrf.disable()
                )


                // =========================
                // STATELESS JWT
                // =========================

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                response.sendError(HttpStatus.UNAUTHORIZED.value())
                        )
                )


                // =========================
                // AUTHORIZATION
                // =========================

                .authorizeHttpRequests(auth -> auth


                        // =========================
                        // PUBLIC
                        // =========================

                        .requestMatchers(
                                "/auth/register",
                                "/auth/login",
                                "/auth/invitations/**",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        .requestMatchers(
                                "/auth/recipients"
                        ).hasAuthority(
                                AuthRecipientsServiceKeyFilter.AUTHORITY
                        )


                        // =========================
                        // OWNER + ADMIN
                        // =========================

                        .requestMatchers(
                                "/members/**"
                        ).hasAnyRole(
                                "OWNER",
                                "ADMIN"
                        )


                        // =========================
                        // AUTHENTICATED
                        // =========================

                        .requestMatchers(
                                "/auth/profile"
                        ).authenticated()


                        // =========================
                        // EVERYTHING ELSE
                        // =========================

                        .anyRequest()
                        .authenticated()
                )


                // =========================
                // JWT FILTER
                // =========================

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
