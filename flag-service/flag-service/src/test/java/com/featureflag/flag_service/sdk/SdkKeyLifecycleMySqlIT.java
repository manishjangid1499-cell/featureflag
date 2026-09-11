package com.featureflag.flag_service.sdk;

import com.featureflag.flag_service.dto.CreateSdkKeyRequest;
import com.featureflag.flag_service.entity.SdkKey;
import com.featureflag.flag_service.repository.SdkKeyRepository;
import com.featureflag.flag_service.service.SdkKeyAuthenticationService;
import com.featureflag.flag_service.service.SdkKeyCredentialService;
import com.featureflag.flag_service.service.SdkKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("mysql-it")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
@Import({
        SdkKeyCredentialService.class,
        SdkKeyService.class,
        SdkKeyAuthenticationService.class,
        SdkKeyLifecycleMySqlIT.ClockConfiguration.class
})
class SdkKeyLifecycleMySqlIT {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11");

    @Autowired
    private SdkKeyService sdkKeyService;

    @Autowired
    private SdkKeyAuthenticationService authenticationService;

    @Autowired
    private SdkKeyCredentialService credentialService;

    @Autowired
    private SdkKeyRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearKeys() {
        repository.deleteAll();
    }

    @Test
    void rawCredentialIsNeverPersistedAndRevocationIsImmediate() {
        var created = sdkKeyService.create(
                new CreateSdkKeyRequest(
                        "Production application",
                        "prod"
                ),
                "owner@example.com"
        );

        SdkKey persisted = repository.findById(
                created.id()
        ).orElseThrow();
        assertThat(created.rawKey()).startsWith("ff_sdk_");
        assertThat(persisted.getKeyHash())
                .isNotEqualTo(created.rawKey())
                .isEqualTo(credentialService.hash(created.rawKey()));
        assertThat(persisted.getKeyPrefix())
                .isEqualTo(created.keyPrefix());
        assertThat(persisted.getEnvironment()).isEqualTo("PROD");
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'sdk_keys'
                  AND column_name IN ('raw_key', 'rawKey')
                """,
                Integer.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM sdk_keys
                WHERE name = ?
                   OR environment = ?
                   OR key_prefix = ?
                   OR key_hash = ?
                   OR created_by = ?
                """,
                Integer.class,
                created.rawKey(),
                created.rawKey(),
                created.rawKey(),
                created.rawKey(),
                created.rawKey()
        )).isZero();

        assertThat(authenticationService.authenticate(
                created.rawKey()
        )).hasValueSatisfying(principal ->
                assertThat(principal.environment())
                        .isEqualTo("PROD")
        );

        var revoked = sdkKeyService.revoke(created.id());
        assertThat(revoked.active()).isFalse();
        assertThat(revoked.revokedAt()).isNotNull();
        assertThat(authenticationService.authenticate(
                created.rawKey()
        )).isEmpty();
    }

    @Test
    void metadataListingNeverReturnsCredentialMaterial() {
        var created = sdkKeyService.create(
                new CreateSdkKeyRequest(
                        "Development application",
                        "DEV"
                ),
                "admin@example.com"
        );

        var metadata = sdkKeyService.list(PageRequest.of(0, 20)).getContent().getFirst();

        assertThat(metadata.id()).isEqualTo(created.id());
        assertThat(metadata.keyPrefix()).isEqualTo(created.keyPrefix());
        assertThat(metadata.environment()).isEqualTo("DEV");
        assertThat(metadata.getClass().getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("rawKey", "keyHash");
    }

    @Test
    void mysqlRejectsDuplicateCredentialHash() {
        var created = sdkKeyService.create(
                new CreateSdkKeyRequest(
                        "First application",
                        "QA"
                ),
                "owner@example.com"
        );
        SdkKey first = repository.findById(created.id())
                .orElseThrow();
        SdkKey duplicate = SdkKey.builder()
                .name("Duplicate hash")
                .environment("QA")
                .keyPrefix("ff_sdk_DUPLICAT")
                .keyHash(first.getKeyHash())
                .active(true)
                .createdAt(Instant.parse("2026-08-27T12:00:00Z"))
                .createdBy("owner@example.com")
                .build();

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(
                    Instant.parse("2026-08-27T12:00:00Z"),
                    ZoneOffset.UTC
            );
        }
    }
}
