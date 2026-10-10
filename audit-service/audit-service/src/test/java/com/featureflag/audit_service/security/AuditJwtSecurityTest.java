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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditController.class)
@ActiveProfiles("test")
@Import({
        SecurityConfig.class,
        AuditJwtSecurityTest.TestJwtConfiguration.class,
        AuditJwtSecurityTest.HealthProbeController.class
})
class AuditJwtSecurityTest {

    private static final String ISSUER = "feature-flag-auth";
    private static final String AUDIENCE = "feature-flag-api";
    private static final String EMAIL = "audit-user@company.com";
    private static final Long DEFAULT_USER_ID = 1L;
    private static final KeyPair SIGNING_KEY = generateRsaKeyPair();

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtDecoder jwtDecoder;
    @Autowired private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter;
    @MockitoBean private AuditService auditService;
    @MockitoBean private AuthAccountStatusClient authAccountStatusClient;

    @BeforeEach
    void setUp() {
        when(auditService.getAllAuditLogs(any(Pageable.class)))
                .thenReturn(Page.empty());
        when(authAccountStatusClient.getAccountStatus(org.mockito.ArgumentMatchers.eq(EMAIL), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(new UserAccountStatus(DEFAULT_USER_ID, EMAIL, "ADMIN", true)));
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
        JWTClaimsSet claims = claims(ISSUER, AUDIENCE, Instant.now().minusSeconds(10),
                Instant.now().plusSeconds(300), "VIEWER");
        SignedJWT token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
                claims
        );
        token.sign(new MACSigner("a-32-byte-minimum-test-secret-key!".getBytes()));

        expectUnauthorized(token.serialize());
    }

    @Test
    void missingBlankAndUnknownRolesAreDenied() throws Exception {
        expectUnauthorized(validToken(null));
        expectUnauthorized(validToken(""));
        expectUnauthorized(validToken("SUPERUSER"));
        expectUnauthorized(validToken("admin"));
    }

    @Test
    void ownerRetainsReadAccess() throws Exception {
        expectAllowedRole("OWNER");
    }

    @Test
    void adminRetainsReadAccess() throws Exception {
        expectAllowedRole("ADMIN");
    }

    @Test
    void developerRetainsReadAccess() throws Exception {
        expectAllowedRole("DEVELOPER");
    }

    @Test
    void viewerRetainsReadAccess() throws Exception {
        expectAllowedRole("VIEWER");
    }

    @Test
    void subjectAndRoleMapExactlyToAuthenticationNameAndAuthority() {
        Jwt jwt = jwtDecoder.decode(validToken("ADMIN"));
        AbstractAuthenticationToken authentication = jwtAuthenticationConverter.convert(jwt);

        assertEquals(EMAIL, authentication.getName());
        assertEquals(1, authentication.getAuthorities().size());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void authenticationUsesTheLocalNimbusJwtDecoder() {
        assertTrue(jwtDecoder instanceof NimbusJwtDecoder);
    }

    @Test
    void healthProbesArePublicButOtherActuatorPathsRemainProtected() throws Exception {
        for (String path : List.of(
                "/actuator/health",
                "/actuator/health/liveness",
                "/actuator/health/readiness"
        )) {
            mockMvc.perform(get(path)).andExpect(status().isOk());
        }

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountIsRejectedWithUnauthorized() throws Exception {
        String token = validToken("ADMIN");
        when(authAccountStatusClient.getAccountStatus(EMAIL, DEFAULT_USER_ID))
                .thenReturn(java.util.Optional.of(new UserAccountStatus(DEFAULT_USER_ID, EMAIL, "ADMIN", false)));

        mockMvc.perform(get("/audit").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletedAccountIsRejectedWithUnauthorized() throws Exception {
        String token = validToken("ADMIN");
        when(authAccountStatusClient.getAccountStatus(EMAIL, DEFAULT_USER_ID))
                .thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/audit").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authServiceOutageRejectsAccessWithServiceUnavailable() throws Exception {
        String token = validToken("ADMIN");
        when(authAccountStatusClient.getAccountStatus(EMAIL, DEFAULT_USER_ID))
                .thenThrow(new AuthServiceUnavailableException("Auth service connection timed out"));

        mockMvc.perform(get("/audit").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void tokenWithOldUserIdIsRejectedWhenAccountIsRecreated() throws Exception {
        String tokenWithUserId = rsaToken(SIGNING_KEY, ISSUER, AUDIENCE,
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(300), "ADMIN", 101L);
        when(authAccountStatusClient.getAccountStatus(EMAIL, 101L))
                .thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/audit").header("Authorization", "Bearer " + tokenWithUserId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenMissingUserIdIsRejectedWithoutCallingAuthService() throws Exception {
        String tokenWithoutUserId = rsaToken(SIGNING_KEY, ISSUER, AUDIENCE,
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(300), "ADMIN", null);

        mockMvc.perform(get("/audit").header("Authorization", "Bearer " + tokenWithoutUserId))
                .andExpect(status().isUnauthorized());

        verify(authAccountStatusClient, never()).getAccountStatus(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void tokenWithMalformedUserIdIsRejectedWithoutCallingAuthService() throws Exception {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject(EMAIL)
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .issueTime(Date.from(Instant.now().minusSeconds(10)))
                .notBeforeTime(Date.from(Instant.now().minusSeconds(10)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .claim("role", "ADMIN")
                .claim("userId", "not-a-number");

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID("test-key").build(),
                builder.build()
        );
        signedJWT.sign(new RSASSASigner((RSAPrivateKey) SIGNING_KEY.getPrivate()));
        String malformedToken = signedJWT.serialize();

        mockMvc.perform(get("/audit").header("Authorization", "Bearer " + malformedToken))
                .andExpect(status().isUnauthorized());

        verify(authAccountStatusClient, never()).getAccountStatus(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void tokenMissingTokenVersionIsRejected() throws Exception {
        JWTClaimsSet claimsWithoutVersion = new JWTClaimsSet.Builder()
                .subject(EMAIL)
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .issueTime(Date.from(Instant.now().minusSeconds(10)))
                .notBeforeTime(Date.from(Instant.now().minusSeconds(10)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .claim("role", "ADMIN")
                .claim("userId", DEFAULT_USER_ID)
                .build();
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID("test-key").build(),
                claimsWithoutVersion
        );
        signedJWT.sign(new RSASSASigner((RSAPrivateKey) SIGNING_KEY.getPrivate()));

        mockMvc.perform(get("/audit").header("Authorization", "Bearer " + signedJWT.serialize()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithStaleTokenVersionIsRejectedWhenAccountCredentialVersionIncremented() throws Exception {
        String staleToken = validToken("ADMIN");

        when(authAccountStatusClient.getAccountStatus(EMAIL, DEFAULT_USER_ID))
                .thenReturn(java.util.Optional.of(new UserAccountStatus(DEFAULT_USER_ID, EMAIL, "ADMIN", true, 2L)));

        mockMvc.perform(get("/audit").header("Authorization", "Bearer " + staleToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validNewTokenForReplacementAccountWorksAccordingToCurrentRole() throws Exception {
        Long replacementUserId = 202L;
        String tokenWithNewUserId = rsaToken(SIGNING_KEY, ISSUER, AUDIENCE,
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(300), "ADMIN", replacementUserId);

        when(authAccountStatusClient.getAccountStatus(EMAIL, replacementUserId))
                .thenReturn(java.util.Optional.of(new UserAccountStatus(replacementUserId, EMAIL, "ADMIN", true)));

        mockMvc.perform(get("/audit").header("Authorization", "Bearer " + tokenWithNewUserId))
                .andExpect(status().isOk());
    }

    @Test
    void accountStatusWithMismatchedUserIdFailsClosed() throws Exception {
        String token = validToken("ADMIN");
        when(authAccountStatusClient.getAccountStatus(EMAIL, DEFAULT_USER_ID))
                .thenReturn(java.util.Optional.of(new UserAccountStatus(999L, EMAIL, "ADMIN", true)));

        mockMvc.perform(get("/audit").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accountStatusWithUnknownRoleFailsClosed() throws Exception {
        String token = validToken("ADMIN");
        when(authAccountStatusClient.getAccountStatus(EMAIL, DEFAULT_USER_ID))
                .thenReturn(java.util.Optional.of(new UserAccountStatus(DEFAULT_USER_ID, EMAIL, "SUPERUSER", true)));

        mockMvc.perform(get("/audit").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private void expectUnauthorized(String token) throws Exception {
        mockMvc.perform(get("/audit").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private void expectAllowedRole(String role) throws Exception {
        when(authAccountStatusClient.getAccountStatus(EMAIL, DEFAULT_USER_ID))
                .thenReturn(java.util.Optional.of(new UserAccountStatus(DEFAULT_USER_ID, EMAIL, role, true)));
        String token = validToken(role);

        mockMvc.perform(get("/audit").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void expectForbiddenRole(String role) throws Exception {
        when(authAccountStatusClient.getAccountStatus(EMAIL, DEFAULT_USER_ID))
                .thenReturn(java.util.Optional.of(new UserAccountStatus(DEFAULT_USER_ID, EMAIL, role, true)));
        String token = validToken(role);

        mockMvc.perform(get("/audit").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private static String validToken(String role) {
        return rsaToken(SIGNING_KEY, ISSUER, AUDIENCE,
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(300), role, DEFAULT_USER_ID);
    }

    private static String rsaToken(
            KeyPair keyPair,
            String issuer,
            String audience,
            Instant notBefore,
            Instant expiresAt,
            String role
    ) {
        return rsaToken(keyPair, issuer, audience, notBefore, expiresAt, role, DEFAULT_USER_ID);
    }

    private static String rsaToken(
            KeyPair keyPair,
            String issuer,
            String audience,
            Instant notBefore,
            Instant expiresAt,
            String role,
            Long userId
    ) {
        try {
            SignedJWT token = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID("test-key").build(),
                    claims(issuer, audience, notBefore, expiresAt, role, userId)
            );
            token.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
            return token.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static JWTClaimsSet claims(String issuer, String audience,
                                       Instant notBefore, Instant expiresAt, String role) {
        return claims(issuer, audience, notBefore, expiresAt, role, DEFAULT_USER_ID);
    }

    private static JWTClaimsSet claims(String issuer, String audience,
                                       Instant notBefore, Instant expiresAt, String role, Long userId) {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(EMAIL).issuer(issuer).audience(audience)
                .issueTime(Date.from(Instant.now().minusSeconds(10)))
                .notBeforeTime(Date.from(notBefore)).expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString());
        if (role != null) claims.claim("role", role);
        if (userId != null) claims.claim("userId", userId);
        claims.claim("tokenVersion", 1L);
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

    @RestController
    static class HealthProbeController {

        @GetMapping({
                "/actuator/health",
                "/actuator/health/liveness",
                "/actuator/health/readiness"
        })
        String health() {
            return "UP";
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
        Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter(
                AuthAccountStatusClient authAccountStatusClient
        ) {
            return new AuthoritativeJwtAuthenticationConverter(authAccountStatusClient);
        }
    }
}
