package com.featureflag.flag_service.config;

import io.lettuce.core.ClientOptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RedisTimeoutConfigurationTest {
    @ParameterizedTest
    @ValueSource(strings = {"local", "docker"})
    void supportedProfilesBoundRedisWaitsAndAllowBackgroundRecovery(String profile) {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + profile)
                .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
                .withUserConfiguration(RedisConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var config = context.getBean(LettuceConnectionFactory.class).getClientConfiguration();
                    assertThat(config.getCommandTimeout()).isEqualTo(Duration.ofMillis(500));
                    var options = config.getClientOptions().orElseThrow();
                    assertThat(options.getSocketOptions().getConnectTimeout()).isEqualTo(Duration.ofMillis(500));
                    assertThat(options.getDisconnectedBehavior())
                            .isEqualTo(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
                    assertThat(options.isAutoReconnect()).isTrue();
                    assertThat(options.getRequestQueueSize()).isEqualTo(256);
                });
    }
}
