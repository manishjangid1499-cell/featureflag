package com.featureflag.analytics_service.security;

import com.featureflag.analytics_service.exception.ApiProblemDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter,
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

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/analytics/**"
                        ).hasAnyRole(
                                "OWNER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                HttpMethod.GET,
                                "/analytics/**"
                        ).hasAnyRole(
                                "OWNER",
                                "ADMIN",
                                "DEVELOPER",
                                "VIEWER"
                        )

                        .anyRequest()
                        .authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                problems.write(request, response, HttpStatus.UNAUTHORIZED,
                                        "unauthenticated", "Unauthorized",
                                        "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) ->
                                problems.write(request, response, HttpStatus.FORBIDDEN,
                                        "forbidden", "Forbidden",
                                        "You do not have permission to access this resource"))
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint((request, response, exception) ->
                                problems.write(request, response, HttpStatus.UNAUTHORIZED,
                                        "unauthenticated", "Unauthorized",
                                        "Authentication is required"))
                );

        return http.build();
    }
}
