package com.featureflag.audit_service.security;

import com.featureflag.audit_service.controller.AuditController;
import com.featureflag.audit_service.service.AuditService;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditController.class)
@Import({SecurityConfig.class, AuditJwtSecurityTest.TestJwtConfiguration.class})
class AuditJwtSecurityTest {

    private static final String ISSUER = "feature-flag-auth";
    private static final String AUDIENCE = "feature-flag-api";
    private static final String EMAIL = "audit-user@company.com";
    private static final KeyPair SIGNING_KEY = generateRsaKeyPair();

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtDecoder jwtDecoder;
    @Autowired private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter;
    @MockitoBean private AuditService auditService;

    @BeforeEach
    void setUp() {
        when(auditService.getAllAuditLogs()).thenReturn(List.of());
    }

    @Test
    void protectedEndpointWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/audit")).andExpect(status().isUnauthorized());
    }

    @Test
    void malformedTokenReturnsUnauthorized() throws Exception {
        expectUnauthorized("not-a-jwt");
    }

    @Test
    void wrongRsaSignatureReturnsUnauthorized() throws Exception {
        expectUnauthorized(rsaToken(generateRsaKeyPair(), ISSUER, AUDIENCE,
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(300), "VIEWER"));
    }

    @Test
    void expiredTokenReturnsUnauthorized() throws Exception {
        expectUnauthorized(rsaToken(SIGNING_KEY, ISSUER, AUDIENCE,
                Instant.now().minusSeconds(300), Instant.now().minusSeconds(120), "VIEWER"));
    }

    @Test
    void futureNotBeforeReturnsUnauthorized() throws Exception {
        expectUnauthorized(rsaToken(SIGNING_KEY, ISSUER, AUDIENCE,
                Instant.now().plusSeconds(300), Instant.now().plusSeconds(600), "VIEWER"));
    }

    @Test
    void wrongIssuerReturnsUnauthorized() throws Exception {
        expectUnauthorized(rsaToken(SIGNING_KEY, "wrong-issuer", AUDIENCE,
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(300), "VIEWER"));
    }

    @Test
    void wrongAudienceReturnsUnauthorized() throws Exception {
        expectUnauthorized(rsaToken(SIGNING_KEY, ISSUER, "wrong-audience",
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(300), "VIEWER"));
    }

    @Test
    void hmacTokenReturnsUnauthorized() throws Exception {
        SignedJWT token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
                claims(ISSUER, AUDIENCE, Instant.now().minusSeconds(10),
                        Instant.now().plusSeconds(300), "VIEWER")
        );
        token.sign(new MACSigner("a-32-byte-minimum-test-secret-key!".getBytes()));
        expectUnauthorized(token.serialize());
    }

    @Test
    void missingBlankLowercaseAndUnknownRolesAreDenied() throws Exception {
        expectUnauthorized(validToken(null));
        expectUnauthorized(validToken(""));
        expectUnauthorized(validToken("admin"));
        expectUnauthorized(validToken("SUPERUSER"));
    }

    @Test
    void allExistingRolePermissionsRemainUnchanged() throws Exception {
        for (String role : List.of("OWNER", "ADMIN", "DEVELOPER", "VIEWER")) {
            mockMvc.perform(get("/audit")
                            .header("Authorization", "Bearer " + validToken(role)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void subjectAndRoleMapExactlyToAuthentication() {
        Jwt jwt = jwtDecoder.decode(validToken("ADMIN"));
        AbstractAuthenticationToken authentication = jwtAuthenticationConverter.convert(jwt);

        assertEquals(EMAIL, authentication.getName());
        assertEquals(1, authentication.getAuthorities().size());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void authenticationUsesLocalNimbusDecoder() {
        assertTrue(jwtDecoder instanceof NimbusJwtDecoder);
    }

    private void expectUnauthorized(String token) throws Exception {
        mockMvc.perform(get("/audit").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private static String validToken(String role) {
        return rsaToken(SIGNING_KEY, ISSUER, AUDIENCE,
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(300), role);
    }

    private static String rsaToken(KeyPair pair, String issuer, String audience,
                                   Instant notBefore, Instant expiresAt, String role) {
        try {
            SignedJWT token = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .type(JOSEObjectType.JWT).keyID("test-key").build(),
                    claims(issuer, audience, notBefore, expiresAt, role)
            );
            token.sign(new RSASSASigner((RSAPrivateKey) pair.getPrivate()));
            return token.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static JWTClaimsSet claims(String issuer, String audience,
                                       Instant notBefore, Instant expiresAt, String role) {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(EMAIL).issuer(issuer).audience(audience)
                .issueTime(Date.from(Instant.now().minusSeconds(10)))
                .notBeforeTime(Date.from(notBefore)).expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString());
        if (role != null) claims.claim("role", role);
        return claims.build();
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @TestConfiguration
    static class TestJwtConfiguration {
        @Bean
        JwtDecoder jwtDecoder() {
            return JwtSecurityConfig.createDecoder(
                    (RSAPublicKey) SIGNING_KEY.getPublic(), ISSUER, AUDIENCE);
        }

        @Bean
        Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
            return new JwtSecurityConfig().jwtAuthenticationConverter();
        }
    }
}
