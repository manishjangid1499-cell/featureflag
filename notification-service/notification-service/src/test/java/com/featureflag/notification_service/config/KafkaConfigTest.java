package com.featureflag.notification_service.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConfigTest {

    @Test
    void consumerFactoryUsesBoundBootstrapServersAndDisablesAutoCommit() {
        KafkaProperties properties =
                kafkaProperties();

        DefaultKafkaConsumerFactory<String, String> factory =
                (DefaultKafkaConsumerFactory<String, String>)
                        new KafkaConfig(properties)
                                .consumerFactory();

        Map<String, Object> configuration =
                factory.getConfigurationProperties();

        assertThat(
                configuration.get(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG
                )
        ).isEqualTo(List.of("kafka:29092"));

        assertThat(configuration)
                .containsEntry(
                        ConsumerConfig.GROUP_ID_CONFIG,
                        "notification-service-group"
                )
                .containsEntry(
                        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                        false
                );

        assertThat(factory.getKeyDeserializer())
                .isInstanceOf(StringDeserializer.class);

        assertThat(factory.getValueDeserializer())
                .isInstanceOf(StringDeserializer.class);
    }

    @Test
    void listenerFactoryUsesRecordAckAndDefaultErrorHandler() {
        KafkaConfig config =
                new KafkaConfig(kafkaProperties());

        DefaultErrorHandler errorHandler =
                config.kafkaErrorHandler(
                        mock(KafkaTemplate.class)
                );

        ConcurrentKafkaListenerContainerFactory<String, String>
                factory =
                config.kafkaListenerContainerFactory(
                        config.consumerFactory(),
                        errorHandler
                );

        assertThat(
                factory.getContainerProperties().getAckMode()
        ).isEqualTo(ContainerProperties.AckMode.RECORD);
        assertThat(errorHandler).isNotNull();
    }

    private KafkaProperties kafkaProperties() {
        KafkaProperties properties =
                new KafkaProperties();

        properties.setBootstrapServers(
                List.of("kafka:29092")
        );
        properties.getConsumer().setGroupId(
                "notification-service-group"
        );

        return properties;
    }
}
