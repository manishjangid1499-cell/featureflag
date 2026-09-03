package com.featureflag.flag_service.security;

import com.featureflag.flag_service.controller.RuntimeEvaluationController;
import com.featureflag.flag_service.controller.SdkKeyController;
import com.featureflag.flag_service.config.TimeConfiguration;
import com.featureflag.flag_service.dto.CreateSdkKeyRequest;
import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import com.featureflag.flag_service.dto.SdkKeyCreatedResponse;
import com.featureflag.flag_service.dto.SdkKeyMetadataResponse;
import com.featureflag.flag_service.service.FlagEvaluationTelemetryService;
import com.featureflag.flag_service.service.SdkKeyAuthenticationService;
import com.featureflag.flag_service.service.SdkKeyService;
import com.featureflag.flag_service.observability.FlagMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        RuntimeEvaluationController.class,
        SdkKeyController.class
})
@Import({
        SecurityConfig.class,
        TimeConfiguration.class,
        SdkRuntimeSecurityTest.TestSecurityConfiguration.class
})
class SdkRuntimeSecurityTest {

    private static final String RAW_KEY =
            "ff_sdk_" + "A".repeat(43);
    private static final String INVALID_KEY =
            "ff_sdk_" + "B".repeat(43);
    private static final String REVOKED_KEY =
            "ff_sdk_" + "C".repeat(43);
    private static final String MALFORMED_KEY =
            "ff_sdk_" + "!".repeat(43);
    private static final SdkKeyPrincipal PRINCIPAL =
            new SdkKeyPrincipal(
                    10L,
                    "Development backend",
                    "DEV",
                    "ff_sdk_AAAAAAAA"
            );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SdkKeyAuthenticationService authenticationService;

    @MockitoBean
    private SdkKeyService sdkKeyService;

    @MockitoBean
    private FlagEvaluationTelemetryService evaluationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private FlagMetrics flagMetrics;

    @BeforeEach
    void setUp() {
        when(authenticationService.authenticate(RAW_KEY))
                .thenReturn(Optional.of(PRINCIPAL));
        when(authenticationService.authenticate(INVALID_KEY))
                .thenReturn(Optional.empty());
        when(authenticationService.authenticate(REVOKED_KEY))
                .thenReturn(Optional.empty());
        when(authenticationService.authenticate(MALFORMED_KEY))
                .thenReturn(Optional.empty());
    }

    @Test
    void validSdkKeyUsesBoundEnvironmentAndSubject() throws Exception {
        when(evaluationService.evaluateFlag(
                "checkout",
                "user 123",
                "DEV"
        )).thenReturn(evaluation("checkout", "DEV", true));

        mockMvc.perform(
                get("/runtime/v1/flags/checkout/evaluate")
                        .header(
                                SdkKeyAuthenticationFilter.HEADER_NAME,
                                RAW_KEY
                        )
                        .queryParam("subject", "user 123")
                        .queryParam("environment", "PROD")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flagKey").value("checkout"))
                .andExpect(jsonPath("$.environment").value("DEV"))
                .andExpect(jsonPath("$.enabled").value(true));

        verify(evaluationService).evaluateFlag(
                "checkout",
                "user 123",
                "DEV"
        );
    }

    @Test
    void disabledDecisionUsesSameTelemetryAwarePath() throws Exception {
        when(evaluationService.evaluateFlag(
                "checkout",
                "excluded-user",
                "DEV"
        )).thenReturn(evaluation("checkout", "DEV", false));

        mockMvc.perform(
                get("/runtime/v1/flags/checkout/evaluate")
                        .header(
                                SdkKeyAuthenticationFilter.HEADER_NAME,
                                RAW_KEY
                        )
                        .queryParam("subject", "excluded-user")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        verify(evaluationService).evaluateFlag(
                "checkout",
                "excluded-user",
                "DEV"
        );
    }

    @Test
    void missingInvalidAndRevokedKeysReturnGenericUnauthorized()
            throws Exception {
        String missingResponse = mockMvc.perform(
                get("/runtime/v1/flags/checkout/evaluate")
                        .queryParam("subject", "user-1")
        ).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(missingResponse)
                .isEqualTo("{\"error\":\"Unauthorized\"}");
        for (String rejectedKey : List.of(
                MALFORMED_KEY,
                INVALID_KEY,
                REVOKED_KEY
        )) {
            String response = mockMvc.perform(
                    get("/runtime/v1/flags/checkout/evaluate")
                            .header(
                                    SdkKeyAuthenticationFilter.HEADER_NAME,
                                    rejectedKey
                            )
                            .queryParam("subject", "user-1")
            ).andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();

            assertThat(response)
                    .isEqualTo("{\"error\":\"Unauthorized\"}")
                    .doesNotContain(
                            rejectedKey,
                            "revoked",
                            "hash"
                    );
        }
        verify(evaluationService, never()).evaluateFlag(
                "checkout",
                "user-1",
                "DEV"
        );
        verify(flagMetrics).sdkAuthenticationFailure("missing");
        verify(flagMetrics, times(3))
                .sdkAuthenticationFailure("invalid");
    }

    @Test
    void credentialInQueryStringIsIgnored() throws Exception {
        mockMvc.perform(
                get("/runtime/v1/flags/checkout/evaluate")
                        .queryParam("subject", "user-1")
                        .queryParam(
                                SdkKeyAuthenticationFilter.HEADER_NAME,
                                RAW_KEY
                        )
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateCredentialHeadersAreRejected() throws Exception {
        mockMvc.perform(
                get("/runtime/v1/flags/checkout/evaluate")
                        .header(
                                SdkKeyAuthenticationFilter.HEADER_NAME,
                                RAW_KEY,
                                INVALID_KEY
                        )
                        .queryParam("subject", "user-1")
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void jwtAloneCannotUseRuntimePlane() throws Exception {
        mockMvc.perform(
                get("/runtime/v1/flags/checkout/evaluate")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .queryParam("subject", "user-1")
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void sdkKeyCannotUseControlPlane() throws Exception {
        mockMvc.perform(
                get("/sdk-keys")
                        .header(
                                SdkKeyAuthenticationFilter.HEADER_NAME,
                                RAW_KEY
                        )
        ).andExpect(status().isUnauthorized());

        mockMvc.perform(
                post("/flags")
                        .header(
                                SdkKeyAuthenticationFilter.HEADER_NAME,
                                RAW_KEY
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
        ).andExpect(status().isUnauthorized());

        mockMvc.perform(
                put("/flags/10")
                        .header(
                                SdkKeyAuthenticationFilter.HEADER_NAME,
                                RAW_KEY
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
        ).andExpect(status().isUnauthorized());

        mockMvc.perform(
                delete("/flags/10")
                        .header(
                                SdkKeyAuthenticationFilter.HEADER_NAME,
                                RAW_KEY
                        )
        ).andExpect(status().isUnauthorized());

        mockMvc.perform(
                patch("/flags/10/toggle")
                        .header(
                                SdkKeyAuthenticationFilter.HEADER_NAME,
                                RAW_KEY
                        )
        ).andExpect(status().isUnauthorized());

        mockMvc.perform(
                post("/sdk-keys")
                        .header(
                                SdkKeyAuthenticationFilter.HEADER_NAME,
                                RAW_KEY
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Escalation attempt",
                                  "environment":"DEV"
                                }
                                """)
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void ownerAndAdminCanManageKeys() throws Exception {
        when(sdkKeyService.create(
                new CreateSdkKeyRequest("Backend", "DEV"),
                "operator@example.com"
        )).thenReturn(createdResponse());
        when(sdkKeyService.list(
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(metadata())));
        when(sdkKeyService.revoke(10L)).thenReturn(
                revokedMetadata()
        );

        for (String role : List.of("OWNER", "ADMIN")) {
            mockMvc.perform(
                    post("/sdk-keys")
                            .with(user("operator@example.com").roles(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name":"Backend",
                                      "environment":"DEV"
                                    }
                                    """)
            ).andExpect(status().isCreated());
            mockMvc.perform(
                    get("/sdk-keys")
                            .with(user("operator@example.com").roles(role))
            ).andExpect(status().isOk());
            mockMvc.perform(
                    post("/sdk-keys/10/revoke")
                            .with(user("operator@example.com").roles(role))
            ).andExpect(status().isOk());
        }
    }

    @Test
    void developerAndViewerCannotManageKeys() throws Exception {
        for (String role : List.of("DEVELOPER", "VIEWER")) {
            mockMvc.perform(
                    post("/sdk-keys")
                            .with(user("operator@example.com").roles(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name":"Backend",
                                      "environment":"DEV"
                                    }
                                    """)
            ).andExpect(status().isForbidden());
            mockMvc.perform(
                    get("/sdk-keys")
                            .with(user("operator@example.com").roles(role))
            ).andExpect(status().isForbidden());
            mockMvc.perform(
                    post("/sdk-keys/10/revoke")
                            .with(user("operator@example.com").roles(role))
            ).andExpect(status().isForbidden());
        }
    }

    @Test
    void rawKeyAppearsOnlyInCreationResponse() throws Exception {
        when(sdkKeyService.create(
                new CreateSdkKeyRequest("Backend", "DEV"),
                "owner@example.com"
        )).thenReturn(createdResponse());
        when(sdkKeyService.list(
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(metadata())));
        when(sdkKeyService.revoke(10L)).thenReturn(
                revokedMetadata()
        );

        String creation = mockMvc.perform(
                post("/sdk-keys")
                        .with(user("owner@example.com").roles("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Backend",
                                  "environment":"DEV"
                                }
                                """)
        ).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String list = mockMvc.perform(
                get("/sdk-keys")
                        .with(user("owner@example.com").roles("OWNER"))
        ).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String revoke = mockMvc.perform(
                post("/sdk-keys/10/revoke")
                        .with(user("owner@example.com").roles("OWNER"))
        ).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(creation).containsOnlyOnce(RAW_KEY);
        assertThat(list).doesNotContain(
                RAW_KEY,
                "rawKey",
                "keyHash"
        );
        assertThat(revoke).doesNotContain(
                RAW_KEY,
                "rawKey",
                "keyHash"
        );
    }

    private FlagEvaluationResponse evaluation(
            String flagKey,
            String environment,
            boolean enabled
    ) {
        return new FlagEvaluationResponse(
                flagKey,
                environment,
                enabled,
                false,
                enabled ? 100 : 0,
                null,
                null,
                true
        );
    }

    private SdkKeyCreatedResponse createdResponse() {
        return new SdkKeyCreatedResponse(
                10L,
                "Backend",
                "DEV",
                "ff_sdk_AAAAAAAA",
                true,
                Instant.parse("2026-08-27T12:00:00Z"),
                "owner@example.com",
                RAW_KEY
        );
    }

    private SdkKeyMetadataResponse metadata() {
        return new SdkKeyMetadataResponse(
                10L,
                "Backend",
                "DEV",
                "ff_sdk_AAAAAAAA",
                true,
                Instant.parse("2026-08-27T12:00:00Z"),
                null,
                "owner@example.com"
        );
    }

    private SdkKeyMetadataResponse revokedMetadata() {
        return new SdkKeyMetadataResponse(
                10L,
                "Backend",
                "DEV",
                "ff_sdk_AAAAAAAA",
                false,
                Instant.parse("2026-08-27T12:00:00Z"),
                Instant.parse("2026-08-27T13:00:00Z"),
                "owner@example.com"
        );
    }

    @TestConfiguration
    static class TestSecurityConfiguration {

        @Bean
        Converter<Jwt, ? extends AbstractAuthenticationToken>
        jwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }
}
