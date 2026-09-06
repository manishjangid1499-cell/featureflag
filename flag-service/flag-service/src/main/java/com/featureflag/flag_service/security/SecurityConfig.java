package com.featureflag.flag_service.security;

import com.featureflag.flag_service.exception.ApiProblemDetails;
import com.featureflag.flag_service.observability.FlagMetrics;
import com.featureflag.flag_service.service.SdkKeyAuthenticationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.core.convert.converter.Converter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain runtimeSecurityFilterChain(
            HttpSecurity http,
            SdkKeyAuthenticationService authenticationService,
            FlagMetrics flagMetrics,
            ApiProblemDetails problems
    ) throws Exception {
        http
                .securityMatcher("/runtime/**")
                .cors(cors -> cors.configurationSource(
                        corsConfigurationSource()
                ))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(
                                (request, response, exception) -> problems.write(
                                        request, response, HttpStatus.UNAUTHORIZED,
                                        "invalid-sdk-key", "Unauthorized",
                                        "A valid SDK key is required"
                                )
                        )
                )
                .addFilterBefore(
                        new SdkKeyAuthenticationFilter(
                                authenticationService,
                                flagMetrics,
                                problems
                        ),
                        AnonymousAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain controlPlaneSecurityFilterChain(
            HttpSecurity http,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter,
            ApiProblemDetails problems
    ) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth

                        // CORS PREFLIGHT
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // SWAGGER / ACTUATOR
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()

                        // SDK KEY MANAGEMENT - JWT CONTROL PLANE
                        .requestMatchers("/sdk-keys/**")
                        .hasAnyRole("OWNER", "ADMIN")

                        // FEATURE FLAGS - RBAC RULES
                        .requestMatchers(HttpMethod.GET, "/flags/**")
                        .hasAnyRole("OWNER", "ADMIN", "DEVELOPER", "VIEWER")

                        .requestMatchers(HttpMethod.POST, "/flags")
                        .hasAnyRole("OWNER", "ADMIN", "DEVELOPER")

                        .requestMatchers(HttpMethod.PUT, "/flags/**")
                        .hasAnyRole("OWNER", "ADMIN", "DEVELOPER")

                        .requestMatchers(HttpMethod.PATCH, "/flags/**")
                        .hasAnyRole("OWNER", "ADMIN", "DEVELOPER")

                        .requestMatchers(HttpMethod.DELETE, "/flags/**")
                        .hasAnyRole("OWNER", "ADMIN")

                        // EVERYTHING ELSE
                        .anyRequest().authenticated()
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With",
                SdkKeyAuthenticationFilter.HEADER_NAME
        ));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
