package com.featureflag.flag_service.infrastructure;

import com.featureflag.flag_service.config.RedisConfig;
import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.repository.FeatureFlagRepository;
import com.featureflag.flag_service.service.FlagService;
import com.featureflag.flag_service.service.OutboxService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class RedisFlagEvaluationInfrastructureIT {

    private static final DockerImageName REDIS_IMAGE =
            DockerImageName.parse("redis:8.10.0");
    private static final String CACHE_KEY =
            "flag:config:DEV:NEW_CHECKOUT";

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(REDIS_IMAGE)
                    .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void connectAndClearRedis() {
        connectionFactory = connectionFactoryFor(REDIS);
        redisTemplate = new RedisConfig()
                .redisTemplate(connectionFactory);
        try (RedisConnection connection =
                     connectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @AfterEach
    void closeRedisConnectionFactory() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void realRedisProvesMissHitTtlAndMutationInvalidation() {
        FeatureFlagRepository repository =
                mock(FeatureFlagRepository.class);
        OutboxService outboxService = mock(OutboxService.class);
        FeatureFlag flag = enabledFlag();
        when(repository.findByFlagKeyAndEnvironment(
                "NEW_CHECKOUT",
                "DEV"
        )).thenReturn(Optional.of(flag));
        when(repository.findById(1L))
                .thenReturn(Optional.of(flag));
        when(repository.save(flag)).thenReturn(flag);

        FlagService service = new FlagService(
                repository,
                redisTemplate,
                new RedisConfig().objectMapper(),
                outboxService
        );

        FlagEvaluationResponse miss = service.evaluateFlag(
                "NEW_CHECKOUT",
                "user-1",
                "DEV"
        );
        FlagEvaluationResponse hit = service.evaluateFlag(
                "NEW_CHECKOUT",
                "user-2",
                "DEV"
        );

        assertThat(miss.isEnabled()).isTrue();
        assertThat(hit.isEnabled()).isTrue();
        assertThat(redisTemplate.hasKey(CACHE_KEY)).isTrue();
        Long ttlSeconds = redisTemplate.getExpire(
                CACHE_KEY,
                TimeUnit.SECONDS
        );
        assertThat(ttlSeconds).isBetween(1L, Duration.ofMinutes(5).toSeconds());
        verify(repository, times(1))
                .findByFlagKeyAndEnvironment(
                        "NEW_CHECKOUT",
                        "DEV"
                );

        service.toggleFlag(1L);

        assertThat(redisTemplate.hasKey(CACHE_KEY)).isFalse();
        FlagEvaluationResponse afterInvalidation =
                service.evaluateFlag(
                        "NEW_CHECKOUT",
                        "user-3",
                        "DEV"
                );
        assertThat(afterInvalidation.isEnabled()).isFalse();
        verify(repository, times(2))
                .findByFlagKeyAndEnvironment(
                        "NEW_CHECKOUT",
                        "DEV"
                );
    }

    @Test
    void realRedisOutageFallsBackToRepository() {
        GenericContainer<?> unavailableRedis =
                new GenericContainer<>(REDIS_IMAGE)
                        .withExposedPorts(6379);
        unavailableRedis.start();
        LettuceConnectionFactory unavailableFactory =
                connectionFactoryFor(unavailableRedis);
        RedisTemplate<String, Object> unavailableTemplate =
                new RedisConfig().redisTemplate(unavailableFactory);
        unavailableTemplate.opsForValue().set(
                "availability-probe",
                "ready"
        );
        unavailableRedis.stop();

        try {
            FeatureFlagRepository repository =
                    mock(FeatureFlagRepository.class);
            FeatureFlag flag = enabledFlag();
            when(repository.findByFlagKeyAndEnvironment(
                    "NEW_CHECKOUT",
                    "DEV"
            )).thenReturn(Optional.of(flag));
            FlagService service = new FlagService(
                    repository,
                    unavailableTemplate,
                    new RedisConfig().objectMapper(),
                    mock(OutboxService.class)
            );

            FlagEvaluationResponse response =
                    service.evaluateFlag(
                            "NEW_CHECKOUT",
                            "user-fallback",
                            "DEV"
                    );

            assertThat(response.isEnabled()).isTrue();
            verify(repository).findByFlagKeyAndEnvironment(
                    "NEW_CHECKOUT",
                    "DEV"
            );
        } finally {
            unavailableFactory.destroy();
        }
    }

    private static LettuceConnectionFactory connectionFactoryFor(
            GenericContainer<?> container
    ) {
        LettuceClientConfiguration clientConfiguration =
                LettuceClientConfiguration.builder()
                        .commandTimeout(Duration.ofMillis(500))
                        .shutdownTimeout(Duration.ZERO)
                        .clientOptions(
                                ClientOptions.builder()
                                        .autoReconnect(false)
                                        .socketOptions(
                                                SocketOptions.builder()
                                                        .connectTimeout(
                                                                Duration.ofMillis(500)
                                                        )
                                                        .build()
                                        )
                                        .build()
                        )
                        .build();
        LettuceConnectionFactory factory =
                new LettuceConnectionFactory(
                        new RedisStandaloneConfiguration(
                                container.getHost(),
                                container.getMappedPort(6379)
                        ),
                        clientConfiguration
                );
        factory.afterPropertiesSet();
        return factory;
    }

    private FeatureFlag enabledFlag() {
        return FeatureFlag.builder()
                .id(1L)
                .version(0L)
                .name("New Checkout")
                .flagKey("NEW_CHECKOUT")
                .environment("DEV")
                .enabled(true)
                .rolloutPercentage(100)
                .targetUsers(List.of())
                .build();
    }
}
