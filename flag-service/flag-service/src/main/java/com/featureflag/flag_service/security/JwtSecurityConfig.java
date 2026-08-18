package com.featureflag.flag_service.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Configuration
public class JwtSecurityConfig {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "OWNER", "ADMIN", "DEVELOPER", "VIEWER"
    );

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties, ResourceLoader resourceLoader) {
        String publicKeyLocation = required(properties.getPublicKeyLocation(), "JWT_PUBLIC_KEY_LOCATION");
        String issuer = required(properties.getIssuer(), "JWT_ISSUER");
        String audience = required(properties.getAudience(), "JWT_AUDIENCE");
        RSAPublicKey publicKey = readPublicKey(publicKeyLocation, resourceLoader);

        return createDecoder(publicKey, issuer, audience);
    }

    @Bean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("sub");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> List.of(
                new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))
        ));
        return converter;
    }

    static JwtDecoder createDecoder(RSAPublicKey publicKey, String issuer, String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                audienceValidator(audience),
                roleValidator()
        ));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String expectedAudience) {
        return jwt -> jwt.getAudience().contains(expectedAudience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "The required audience is missing", null
                ));
    }

    private static OAuth2TokenValidator<Jwt> roleValidator() {
        return jwt -> {
            String role = jwt.getClaimAsString("role");
            return role != null && ALLOWED_ROLES.contains(role)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                            "invalid_token", "The role claim is missing, blank, or unsupported", null
                    ));
        };
    }

    private static RSAPublicKey readPublicKey(String location, ResourceLoader resourceLoader) {
        try {
            byte[] encoded = decodePem(resourceLoader.getResource(location));
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(encoded));
            if (publicKey.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException("RSA public key must be at least 2048 bits");
            }
            return publicKey;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load the configured RSA public key", exception);
        }
    }

    private static byte[] decodePem(Resource resource) throws IOException {
        String pem;
        try (var input = resource.getInputStream()) {
            pem = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
        }
        String encoded = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        if (encoded.isBlank()) {
            throw new IllegalArgumentException("Configured public key resource is empty");
        }
        return Base64.getDecoder().decode(encoded);
    }

    private static String required(String value, String setting) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(setting + " must be configured");
        }
        return value.trim();
    }
}
