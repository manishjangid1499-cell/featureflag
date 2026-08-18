package com.featureflag.auth_service.security;

import com.featureflag.auth_service.dto.AuthResponse;
import com.featureflag.auth_service.dto.LoginRequest;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.repository.UserRepository;
import com.featureflag.auth_service.service.AuthService;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceTest {

    private static final String ISSUER = "feature-flag-auth-test";
    private static final String AUDIENCE = "feature-flag-api-test";
    private static final String KEY_ID = "test-rsa-key";

    private KeyPair keyPair;
    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = generateRsaKeyPair();
        jwtService = serviceFor(keyPair, ISSUER, AUDIENCE, Duration.ofMinutes(15));
    }

    @Test
    void generatedTokenHasRequiredRs256HeaderAndClaims() {
        Instant before = Instant.now();
        String token = jwtService.generateToken("  User@Company.COM ", "admin");
        Instant after = Instant.now();

        Jwt jwt = jwtService.decode(token);

        assertEquals("RS256", jwt.getHeaders().get("alg"));
        assertEquals("JWT", jwt.getHeaders().get("typ"));
        assertEquals(KEY_ID, jwt.getHeaders().get("kid"));
        assertEquals("user@company.com", jwt.getSubject());
        assertEquals("ADMIN", jwt.getClaimAsString("role"));
        assertEquals(ISSUER, jwt.getClaimAsString("iss"));
        assertEquals(List.of(AUDIENCE), jwt.getAudience());
        assertNotNull(jwt.getIssuedAt());
        assertNotNull(jwt.getNotBefore());
        assertNotNull(jwt.getExpiresAt());
        assertNotNull(jwt.getId());
        assertFalse(jwt.getId().isBlank());
        assertFalse(jwt.getIssuedAt().isBefore(before.minusSeconds(1)));
        assertFalse(jwt.getIssuedAt().isAfter(after.plusSeconds(1)));
        assertEquals(jwt.getIssuedAt(), jwt.getNotBefore());

        long lifetimeSeconds = Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds();
        assertTrue(lifetimeSeconds >= 899 && lifetimeSeconds <= 901);
    }

    @Test
    void matchingPublicKeyValidatesToken() {
        String token = jwtService.generateToken("user@company.com", "VIEWER");

        assertTrue(jwtService.isTokenValid(token));
        assertEquals("user@company.com", jwtService.extractEmail(token));
        assertEquals("VIEWER", jwtService.extractRole(token));
    }

    @Test
    void anotherRsaKeyCannotValidateToken() throws Exception {
        JwtService otherService = serviceFor(generateRsaKeyPair(), ISSUER, AUDIENCE, Duration.ofMinutes(15));
        String token = jwtService.generateToken("user@company.com", "VIEWER");

        assertFalse(otherService.isTokenValid(token));
        assertThrows(JwtException.class, () -> otherService.decode(token));
    }

    @Test
    void mismatchedConfiguredKeyPairIsRejectedAtStartup() throws Exception {
        KeyPair otherPair = generateRsaKeyPair();

        assertThrows(IllegalStateException.class, () -> new JwtService(
                (RSAPrivateKey) keyPair.getPrivate(),
                (RSAPublicKey) otherPair.getPublic(),
                ISSUER,
                AUDIENCE,
                Duration.ofMinutes(15),
                KEY_ID
        ));
    }

    @Test
    void hmacTokenIsRejected() throws Exception {
        JWTClaimsSet claims = baseClaims(ISSUER, AUDIENCE, Instant.now().plusSeconds(60), "VIEWER");
        SignedJWT hmacJwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).keyID("hmac-key").build(),
                claims
        );
        hmacJwt.sign(new MACSigner("a-32-byte-minimum-test-secret-key!".getBytes()));

        assertFalse(jwtService.isTokenValid(hmacJwt.serialize()));
        assertThrows(JwtException.class, () -> jwtService.decode(hmacJwt.serialize()));
    }

    @Test
    void expiredTokenIsRejected() {
        String token = signedToken(keyPair, ISSUER, AUDIENCE, Instant.now().minusSeconds(120), "VIEWER");

        assertFalse(jwtService.isTokenValid(token));
    }

    @Test
    void incorrectIssuerIsRejected() {
        String token = signedToken(keyPair, "another-issuer", AUDIENCE, Instant.now().plusSeconds(60), "VIEWER");

        assertFalse(jwtService.isTokenValid(token));
    }

    @Test
    void incorrectAudienceIsRejected() {
        String token = signedToken(keyPair, ISSUER, "another-audience", Instant.now().plusSeconds(60), "VIEWER");

        assertFalse(jwtService.isTokenValid(token));
    }

    @Test
    void missingAndUnknownRolesAreRejected() {
        String missingRole = signedToken(keyPair, ISSUER, AUDIENCE, Instant.now().plusSeconds(60), null);
        String unknownRole = signedToken(keyPair, ISSUER, AUDIENCE, Instant.now().plusSeconds(60), "SUPERUSER");

        assertFalse(jwtService.isTokenValid(missingRole));
        assertFalse(jwtService.isTokenValid(unknownRole));
        assertThrows(IllegalArgumentException.class,
                () -> jwtService.generateToken("user@company.com", "SUPERUSER"));
    }

    @Test
    void loginShapeUsesNewRsaToken() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AuthService authService = new AuthService(userRepository, passwordEncoder, jwtService);
        User loginUser = User.builder()
                .email("User@Company.COM")
                .password("encoded")
                .role(Role.ADMIN)
                .build();
        LoginRequest request = new LoginRequest();
        request.setEmail("User@Company.COM");
        request.setPassword("password");

        when(userRepository.findByEmail("User@Company.COM")).thenReturn(Optional.of(loginUser));
        when(passwordEncoder.matches("password", "encoded")).thenReturn(true);

        AuthResponse response = authService.login(request);

        assertNotNull(response.getToken());
        assertEquals("User@Company.COM", response.getEmail());
        assertEquals("ADMIN", response.getRole());
        assertEquals("RS256", jwtService.decode(response.getToken()).getHeaders().get("alg"));
    }

    private static JwtService serviceFor(KeyPair pair, String issuer, String audience, Duration ttl) {
        return new JwtService(
                (RSAPrivateKey) pair.getPrivate(),
                (RSAPublicKey) pair.getPublic(),
                issuer,
                audience,
                ttl,
                KEY_ID
        );
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String signedToken(
            KeyPair pair,
            String issuer,
            String audience,
            Instant expiresAt,
            String role
    ) {
        try {
            var rsaKey = new com.nimbusds.jose.jwk.RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(KEY_ID)
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(KEY_ID).build(),
                    baseClaims(issuer, audience, expiresAt, role)
            );
            jwt.sign(new com.nimbusds.jose.crypto.RSASSASigner(rsaKey));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static JWTClaimsSet baseClaims(
            String issuer,
            String audience,
            Instant expiresAt,
            String role
    ) {
        Instant issuedAt = Instant.now().minusSeconds(30);
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject("user@company.com")
                .issuer(issuer)
                .audience(audience)
                .issueTime(Date.from(issuedAt))
                .notBeforeTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString());
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.build();
    }
}
