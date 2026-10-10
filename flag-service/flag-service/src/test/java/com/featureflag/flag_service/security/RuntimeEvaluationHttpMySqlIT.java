package com.featureflag.flag_service.security;

import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.entity.SdkKey;
import com.featureflag.flag_service.repository.FeatureFlagRepository;
import com.featureflag.flag_service.repository.SdkKeyRepository;
import com.featureflag.flag_service.service.EvaluationTelemetryPublisher;
import com.featureflag.flag_service.service.SdkKeyCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mysql-it")
@TestPropertySource(properties = {
        "security.jwt.public-key-location=classpath:test-public-key.pem",
        "security.jwt.issuer=feature-flag-auth",
        "security.jwt.audience=feature-flag-api",
        "features.evaluation.config-cache-enabled=false",
        "management.health.redis.enabled=false",
        "management.health.kafka.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class RuntimeEvaluationHttpMySqlIT {

    private static final String RUNTIME_HEADER = "X-Feature-Flag-Key";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeatureFlagRepository flagRepository;

    @Autowired
    private SdkKeyRepository sdkKeyRepository;

    @Autowired
    private SdkKeyCredentialService credentialService;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @MockBean
    private AuthAccountStatusClient authAccountStatusClient;

    @MockBean
    private EvaluationTelemetryPublisher telemetryPublisher;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void cleanDatabase() {
        flagRepository.deleteAll();
        sdkKeyRepository.deleteAll();
    }

    @Test
    void headerConstantMatchesSdkKeyAuthenticationFilterConstant() {
        assertThat(SdkKeyAuthenticationFilter.HEADER_NAME)
                .isEqualTo("X-Feature-Flag-Key")
                .isNotEqualTo("X-SDK-Key");
    }

    @Test
    void runtimeEvaluationWithPersistedSdkKeyReflectsImmediatelyWhenFlagMutatedInMySql() throws Exception {
        // 1. Persist real SDK key in MySQL for DEV
        var generatedKey = credentialService.generate();
        sdkKeyRepository.saveAndFlush(
                SdkKey.builder()
                        .name("Checkout Service Backend")
                        .environment("DEV")
                        .keyPrefix(generatedKey.keyPrefix())
                        .keyHash(generatedKey.keyHash())
                        .active(true)
                        .createdAt(Instant.now())
                        .createdBy("owner@example.com")
                        .build()
        );

        // 2. Seed feature flag in MySQL with enabled = true
        FeatureFlag flag = flagRepository.saveAndFlush(
                FeatureFlag.builder()
                        .flagKey("checkout-flow")
                        .name("New Checkout Flow")
                        .environment("DEV")
                        .enabled(true)
                        .rolloutPercentage(100)
                        .build()
        );

        // 3. Evaluate flag via HTTP endpoint with X-Feature-Flag-Key header
        mockMvc.perform(get("/runtime/v1/flags/checkout-flow/evaluate")
                        .param("subject", "user-100")
                        .header(SdkKeyAuthenticationFilter.HEADER_NAME, generatedKey.rawKey())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flagKey").value("checkout-flow"))
                .andExpect(jsonPath("$.environment").value("DEV"))
                .andExpect(jsonPath("$.enabled").value(true));

        // 4. Commit flag mutation in an independent transaction that finishes before the next HTTP call
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.executeWithoutResult(txStatus -> {
            FeatureFlag current = flagRepository.findById(flag.getId()).orElseThrow();
            current.setEnabled(false);
            flagRepository.saveAndFlush(current);
        });

        // 5. Evaluate flag again via HTTP endpoint: with cache disabled, immediately reflects false
        mockMvc.perform(get("/runtime/v1/flags/checkout-flow/evaluate")
                        .param("subject", "user-100")
                        .header(SdkKeyAuthenticationFilter.HEADER_NAME, generatedKey.rawKey())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flagKey").value("checkout-flow"))
                .andExpect(jsonPath("$.environment").value("DEV"))
                .andExpect(jsonPath("$.enabled").value(false));

        // 6. Confirm no human account lookup was ever performed
        verifyNoInteractions(authAccountStatusClient);
    }

    @Test
    void environmentIsolationEnforcesCredentialScopeAndPreventsCrossEnvironmentAccess() throws Exception {
        // 1. Persist DEV SDK key and PROD SDK key in MySQL
        var devGenKey = credentialService.generate();
        sdkKeyRepository.saveAndFlush(
                SdkKey.builder()
                        .name("DEV Service Key")
                        .environment("DEV")
                        .keyPrefix(devGenKey.keyPrefix())
                        .keyHash(devGenKey.keyHash())
                        .active(true)
                        .createdAt(Instant.now())
                        .createdBy("admin@example.com")
                        .build()
        );

        var prodGenKey = credentialService.generate();
        sdkKeyRepository.saveAndFlush(
                SdkKey.builder()
                        .name("PROD Service Key")
                        .environment("PROD")
                        .keyPrefix(prodGenKey.keyPrefix())
                        .keyHash(prodGenKey.keyHash())
                        .active(true)
                        .createdAt(Instant.now())
                        .createdBy("admin@example.com")
                        .build()
        );

        // 2. Seed PROD-only flag and DEV-only flag in MySQL
        flagRepository.saveAndFlush(
                FeatureFlag.builder()
                        .flagKey("prod-payment-v2")
                        .name("Payment Gateway V2")
                        .environment("PROD")
                        .enabled(true)
                        .rolloutPercentage(100)
                        .build()
        );

        flagRepository.saveAndFlush(
                FeatureFlag.builder()
                        .flagKey("dev-debug-panel")
                        .name("Debug Panel")
                        .environment("DEV")
                        .enabled(true)
                        .rolloutPercentage(100)
                        .build()
        );

        // 3. DEV key attempting to evaluate PROD flag receives 404 (environment isolation)
        mockMvc.perform(get("/runtime/v1/flags/prod-payment-v2/evaluate")
                        .param("subject", "user-42")
                        .header(SdkKeyAuthenticationFilter.HEADER_NAME, devGenKey.rawKey())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // 4. Even if query parameter tries to override environment, principal environment DEV is authoritative
        mockMvc.perform(get("/runtime/v1/flags/prod-payment-v2/evaluate")
                        .param("subject", "user-42")
                        .param("environment", "PROD")
                        .header(SdkKeyAuthenticationFilter.HEADER_NAME, devGenKey.rawKey())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // 5. PROD key successfully evaluates PROD flag
        mockMvc.perform(get("/runtime/v1/flags/prod-payment-v2/evaluate")
                        .param("subject", "user-42")
                        .header(SdkKeyAuthenticationFilter.HEADER_NAME, prodGenKey.rawKey())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flagKey").value("prod-payment-v2"))
                .andExpect(jsonPath("$.environment").value("PROD"))
                .andExpect(jsonPath("$.enabled").value(true));

        // 6. PROD key cannot evaluate DEV flag
        mockMvc.perform(get("/runtime/v1/flags/dev-debug-panel/evaluate")
                        .param("subject", "user-42")
                        .header(SdkKeyAuthenticationFilter.HEADER_NAME, prodGenKey.rawKey())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // 7. Confirm no human account lookup was ever performed
        verifyNoInteractions(authAccountStatusClient);
    }

    @Test
    void authenticationRejectsMissingInvalidOrRevokedSdkKeys() throws Exception {
        // 1. Missing header returns 401
        mockMvc.perform(get("/runtime/v1/flags/any-flag/evaluate")
                        .param("subject", "user-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.type").value("urn:feature-flag-platform:problem:invalid-sdk-key"))
                .andExpect(jsonPath("$.properties.code").value("invalid-sdk-key"));

        // 2. Wrong header name (e.g. X-SDK-Key instead of X-Feature-Flag-Key) returns 401
        var generatedKey = credentialService.generate();
        mockMvc.perform(get("/runtime/v1/flags/any-flag/evaluate")
                        .param("subject", "user-1")
                        .header("X-SDK-Key", generatedKey.rawKey())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // 3. Unrecognized key returns 401
        mockMvc.perform(get("/runtime/v1/flags/any-flag/evaluate")
                        .param("subject", "user-1")
                        .header(SdkKeyAuthenticationFilter.HEADER_NAME, generatedKey.rawKey())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // 4. Persist key and then revoke it: revoked key returns 401
        SdkKey savedKey = sdkKeyRepository.saveAndFlush(
                SdkKey.builder()
                        .name("Revoked Key Test")
                        .environment("DEV")
                        .keyPrefix(generatedKey.keyPrefix())
                        .keyHash(generatedKey.keyHash())
                        .active(false)
                        .revokedAt(Instant.now())
                        .createdAt(Instant.now())
                        .createdBy("admin@example.com")
                        .build()
        );

        mockMvc.perform(get("/runtime/v1/flags/any-flag/evaluate")
                        .param("subject", "user-1")
                        .header(SdkKeyAuthenticationFilter.HEADER_NAME, generatedKey.rawKey())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // 5. Confirm no human account lookup was ever performed
        verifyNoInteractions(authAccountStatusClient);
    }
}
