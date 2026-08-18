package com.featureflag.auth_service.security;

import com.featureflag.auth_service.config.JwtProperties;
import com.featureflag.auth_service.entity.Role;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class JwtService {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            Role.OWNER.name(),
            Role.ADMIN.name(),
            Role.DEVELOPER.name(),
            Role.VIEWER.name()
    );

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final String issuer;
    private final String audience;
    private final Duration accessTokenTtl;
    private final String keyId;

    @Autowired
    public JwtService(JwtProperties properties, ResourceLoader resourceLoader) {
        this(
                readPrivateKey(required(properties.getPrivateKeyLocation(), "AUTH_JWT_PRIVATE_KEY_LOCATION"), resourceLoader),
                readPublicKey(required(properties.getPublicKeyLocation(), "JWT_PUBLIC_KEY_LOCATION"), resourceLoader),
                required(properties.getIssuer(), "JWT_ISSUER"),
                required(properties.getAudience(), "JWT_AUDIENCE"),
                required(properties.getAccessTokenTtl(), "JWT_ACCESS_TOKEN_TTL"),
                required(properties.getKeyId(), "JWT_KEY_ID")
        );
    }

    JwtService(
            RSAPrivateKey privateKey,
            RSAPublicKey publicKey,
            String issuer,
            String audience,
            Duration accessTokenTtl,
            String keyId
    ) {
        if (accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalStateException("JWT_ACCESS_TOKEN_TTL must be positive");
        }
        if (!privateKey.getModulus().equals(publicKey.getModulus())) {
            throw new IllegalStateException("Configured RSA private and public keys do not match");
        }

        this.issuer = issuer;
        this.audience = audience;
        this.accessTokenTtl = accessTokenTtl;
        this.keyId = keyId;

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .algorithm(JWSAlgorithm.RS256)
                .keyID(keyId)
                .build();
        this.jwtEncoder = new NimbusJwtEncoder(
                new ImmutableJWKSet<>(new JWKSet(rsaKey))
        );

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                audienceValidator(audience),
                roleValidator()
        ));
        this.jwtDecoder = decoder;
    }

    public String generateToken(String email, String role) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedRole = normalizeRole(role);
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .type(JOSEObjectType.JWT.getType())
                .keyId(keyId)
                .build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(normalizedEmail)
                .claim("role", normalizedRole)
                .issuer(issuer)
                .audience(java.util.List.of(audience))
                .issuedAt(issuedAt)
                .notBefore(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    public String extractEmail(String token) {
        return decode(token).getSubject();
    }

    public String extractRole(String token) {
        return decode(token).getClaimAsString("role");
    }

    public boolean isTokenValid(String token, String email) {
        try {
            return decode(token).getSubject().equals(normalizeEmail(email));
        } catch (Exception exception) {
            return false;
        }
    }

    public boolean isTokenValid(String token) {
        try {
            decode(token);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    Jwt decode(String token) {
        return jwtDecoder.decode(token);
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
                            "invalid_token", "The role claim is missing or unsupported", null
                    ));
        };
    }

    private static String normalizeEmail(String email) {
        String normalized = required(email, "JWT subject email")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("JWT subject email must not be blank");
        }
        return normalized;
    }

    private static String normalizeRole(String role) {
        String normalized = required(role, "JWT role").trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ROLES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported JWT role");
        }
        return normalized;
    }

    private static RSAPrivateKey readPrivateKey(String location, ResourceLoader loader) {
        try {
            byte[] encoded = decodePem(loader.getResource(location), "PRIVATE KEY");
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load the configured RSA private key", exception);
        }
    }

    private static RSAPublicKey readPublicKey(String location, ResourceLoader loader) {
        try {
            byte[] encoded = decodePem(loader.getResource(location), "PUBLIC KEY");
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load the configured RSA public key", exception);
        }
    }

    private static byte[] decodePem(Resource resource, String type) throws IOException {
        String pem;
        try (var input = resource.getInputStream()) {
            pem = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
        }
        String encoded = pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        if (encoded.isBlank()) {
            throw new IllegalArgumentException("Configured key resource is empty");
        }
        return Base64.getDecoder().decode(encoded);
    }

    private static String required(String value, String setting) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(setting + " must be configured");
        }
        return value.trim();
    }

    private static <T> T required(T value, String setting) {
        if (value == null) {
            throw new IllegalStateException(setting + " must be configured");
        }
        return value;
    }
}
