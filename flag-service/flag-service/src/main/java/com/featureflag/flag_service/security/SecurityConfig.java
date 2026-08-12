package com.featureflag.flag_service.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtValidationFilter jwtValidationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

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
                                "/v3/api-docs/**",
                                "/actuator/**"
                        ).permitAll()

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
                .addFilterBefore(
                        jwtValidationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}