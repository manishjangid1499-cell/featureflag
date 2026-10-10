package com.featureflag.notification_service.security;

import com.featureflag.notification_service.exception.ApiProblemDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
                    internalNotificationServiceKeyFilter,
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
                                HttpMethod.POST,
                                InternalNotificationServiceKeyFilter.INVITATION_PATH,
                                InternalNotificationServiceKeyFilter.PASSWORD_RESET_PATH
                        ).hasAuthority(
                                InternalNotificationServiceKeyFilter.AUTHORITY
                        )

                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()

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

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/notifications/**"
                        ).hasAnyRole(
                                "OWNER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/notifications/**"
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
                                handleAuthenticationFailure(request, response, exception, problems))
                        .accessDeniedHandler((request, response, exception) ->
                                problems.write(request, response, HttpStatus.FORBIDDEN,
                                        "forbidden", "Forbidden",
                                        "You do not have permission to access this resource"))
                )

                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        jwtAuthenticationConverter
                                )
                        ).authenticationEntryPoint((request, response, exception) ->
                                handleAuthenticationFailure(request, response, exception, problems))
                )

                .addFilterBefore(
                        internalNotificationServiceKeyFilter,
                        BearerTokenAuthenticationFilter.class
                );

        return http.build();
    }

    private static void handleAuthenticationFailure(
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            org.springframework.security.core.AuthenticationException exception,
            ApiProblemDetails problems
    ) throws java.io.IOException {
        if (isServiceUnavailable(exception)) {
            problems.write(
                    request,
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "service-unavailable",
                    "Service Unavailable",
                    "Authentication provider is temporarily unavailable"
            );
            return;
        }
        problems.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "unauthenticated",
                "Unauthorized",
                "Authentication is required"
        );
    }

    private static boolean isServiceUnavailable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof AuthServiceUnavailableException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
