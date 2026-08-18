package com.featureflag.flag_service.security;

import com.featureflag.flag_service.config.OpenApiConfig;
import com.featureflag.flag_service.controller.FlagController;
import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.service.FlagService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FlagController.class)
@Import({SecurityConfig.class, OpenApiConfig.class, FlagJwtSecurityTest.TestJwtConfiguration.class})
class FlagJwtSecurityTest {

    private static final String ISSUER = "feature-flag-auth";
    private static final String AUDIENCE = "feature-flag-api";
    private static final String EMAIL = "user@company.com";
    private static final KeyPair SIGNING_KEY = generateRsaKeyPair();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter;

    @MockitoBean
    private FlagService flagService;

    @BeforeEach
    void setUp() {
        when(flagService.getAllFlags()).thenReturn(List.of());
        when(flagService.toggleFlag(1L)).thenReturn(FeatureFlag.builder().id(1L).build());
        when(flagService.deleteFlag(1L)).thenReturn("deleted");
    }

    @Test
    void protectedEndpointWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/flags")).andExpect(status().isUnauthorized());
    }

    @Test
    void malformedTokenReturnsUnauthorized() throws Exception {
        expectUnauthorized("not-a-jwt");
    }

    @Test
    void tokenSignedByAnotherRsaKeyReturnsUnauthorized() throws Exception {
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
        expectUnauthorized(validTimingToken(null));
        expectUnauthorized(validTimingToken(""));
        expectUnauthorized(validTimingToken("SUPERUSER"));
        expectUnauthorized(validTimingToken("admin"));
    }

    @Test
    void ownerRetainsReadPatchAndDeleteAccess() throws Exception {
        expectAllowedRole("OWNER", true, true);
    }

    @Test
    void adminRetainsReadPatchAndDeleteAccess() throws Exception {
        expectAllowedRole("ADMIN", true, true);
    }

    @Test
    void developerRetainsReadAndPatchButNotDeleteAccess() throws Exception {
        expectAllowedRole("DEVELOPER", true, false);
    }

    @Test
    void viewerRetainsReadOnlyAccess() throws Exception {
        expectAllowedRole("VIEWER", false, false);
    }

    @Test
    void subjectAndRoleMapExactlyToAuthenticationNameAndAuthority() {
        Jwt jwt = jwtDecoder.decode(validTimingToken("ADMIN"));
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

    private void expectUnauthorized(String token) throws Exception {
        mockMvc.perform(get("/flags").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private void expectAllowedRole(String role, boolean patchAllowed, boolean deleteAllowed) throws Exception {
        String token = validTimingToken(role);

        mockMvc.perform(get("/flags").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/flags/1/toggle").header("Authorization", "Bearer " + token))
                .andExpect(patchAllowed ? status().isOk() : status().isForbidden());
        mockMvc.perform(delete("/flags/1").header("Authorization", "Bearer " + token))
                .andExpect(deleteAllowed ? status().isOk() : status().isForbidden());
    }

    private static String validTimingToken(String role) {
        return rsaToken(SIGNING_KEY, ISSUER, AUDIENCE,
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(300), role);
    }

    private static String rsaToken(
            KeyPair keyPair,
            String issuer,
            String audience,
            Instant notBefore,
            Instant expiresAt,
            String role
    ) {
        try {
            SignedJWT token = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .type(JOSEObjectType.JWT)
                            .keyID("test-key")
                            .build(),
                    claims(issuer, audience, notBefore, expiresAt, role)
            );
            token.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
            return token.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static JWTClaimsSet claims(
            String issuer,
            String audience,
            Instant notBefore,
            Instant expiresAt,
            String role
    ) {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(EMAIL)
                .issuer(issuer)
                .audience(audience)
                .issueTime(Date.from(Instant.now().minusSeconds(10)))
                .notBeforeTime(Date.from(notBefore))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString());
        if (role != null) {
            claims.claim("role", role);
        }
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
                    (RSAPublicKey) SIGNING_KEY.getPublic(), ISSUER, AUDIENCE
            );
        }

        @Bean
        Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
            return new JwtSecurityConfig().jwtAuthenticationConverter();
        }
    }
}
