package com.featureflag.analytics_service.config;

import com.featureflag.analytics_service.event.FlagEvent;
import com.featureflag.analytics_service.kafka.AnalyticsEventConsumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.SerializationUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class KafkaConfigTest {

    private static final String TRUSTED_EVENT_PACKAGE =
            "com.featureflag.analytics_service.event";

    @Test
    void consumerFactoryUsesErrorHandlingDeserializerAndDisablesAutoCommit() {
        KafkaProperties properties = kafkaProperties();

        DefaultKafkaConsumerFactory<String, FlagEvent> factory =
                (DefaultKafkaConsumerFactory<String, FlagEvent>)
                        new KafkaConfig(properties).consumerFactory();

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
                        "analytics-group"
                )
                .containsEntry(
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                        "earliest"
                )
                .containsEntry(
                        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                        false
                )
                .containsEntry(
                        JsonDeserializer.TRUSTED_PACKAGES,
                        TRUSTED_EVENT_PACKAGE
                )
                .containsEntry(
                        JsonDeserializer.USE_TYPE_INFO_HEADERS,
                        "false"
                )
                .containsEntry(
                        JsonDeserializer.VALUE_DEFAULT_TYPE,
                        FlagEvent.class.getName()
                );

        assertThat(
                configuration.get(
                        JsonDeserializer.TRUSTED_PACKAGES
                )
        ).isNotEqualTo("*");

        assertThat(factory.getKeyDeserializer())
                .isInstanceOf(StringDeserializer.class);

        assertThat(factory.getValueDeserializer())
                .isInstanceOf(
                        ErrorHandlingDeserializer.class
                );

        assertThatCode(
                () -> factory.getValueDeserializer()
                        .configure(configuration, false)
        ).doesNotThrowAnyException();
    }

    @Test
    void fixedLocalTypeDeserializesAndIgnoresForeignTypeHeader() {
        KafkaProperties properties = kafkaProperties();

        DefaultKafkaConsumerFactory<String, FlagEvent> factory =
                (DefaultKafkaConsumerFactory<String, FlagEvent>)
                        new KafkaConfig(properties).consumerFactory();

        Map<String, Object> configuration =
                factory.getConfigurationProperties();

        ErrorHandlingDeserializer<FlagEvent> deserializer =
                (ErrorHandlingDeserializer<FlagEvent>)
                        factory.getValueDeserializer();

        deserializer.configure(configuration, false);

        RecordHeaders headers = new RecordHeaders();
        headers.add(
                "__TypeId__",
                "java.lang.Runtime"
                        .getBytes(StandardCharsets.UTF_8)
        );

        FlagEvent event = deserializer.deserialize(
                "feature-flag-events",
                headers,
                """
                {
                  "eventType":"UPDATED",
                  "flagKey":"checkout",
                  "timestamp":"2026-08-19T10:00:00Z"
                }
                """.getBytes(StandardCharsets.UTF_8)
        );

        assertThat(event).isInstanceOf(FlagEvent.class);
        assertThat(event.getEventType())
                .isEqualTo("UPDATED");
        assertThat(event.getFlagKey())
                .isEqualTo("checkout");
        assertThat(event.getTimestamp())
                .isEqualTo("2026-08-19T10:00:00Z");
    }

    @Test
    void malformedPayloadIsCapturedForContainerErrorHandling() {
        KafkaProperties properties = kafkaProperties();

        DefaultKafkaConsumerFactory<String, FlagEvent> factory =
                (DefaultKafkaConsumerFactory<String, FlagEvent>)
                        new KafkaConfig(properties).consumerFactory();

        Map<String, Object> configuration =
                factory.getConfigurationProperties();

        ErrorHandlingDeserializer<FlagEvent> deserializer =
                (ErrorHandlingDeserializer<FlagEvent>)
                        factory.getValueDeserializer();

        deserializer.configure(configuration, false);

        RecordHeaders headers = new RecordHeaders();

        FlagEvent event = deserializer.deserialize(
                "feature-flag-events",
                headers,
                "{not-json".getBytes(StandardCharsets.UTF_8)
        );

        assertThat(event).isNull();
        assertThat(
                headers.lastHeader(
                        SerializationUtils
                                .VALUE_DESERIALIZER_EXCEPTION_HEADER
                )
        ).isNotNull();
    }

    @Test
    void listenerFactoryUsesRecordAckAndDefaultErrorHandler() {
        KafkaConfig config =
                new KafkaConfig(kafkaProperties());

        DefaultErrorHandler errorHandler =
                config.kafkaErrorHandler(
                        mock(KafkaTemplate.class)
                );

        ConcurrentKafkaListenerContainerFactory<String, FlagEvent>
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

    @Test
    void listenerGroupIdRemainsAnalyticsGroup()
            throws NoSuchMethodException {

        KafkaListener listener =
                AnalyticsEventConsumer.class
                        .getMethod(
                                "consume",
                                FlagEvent.class
                        )
                        .getAnnotation(KafkaListener.class);

        assertThat(listener).isNotNull();
        assertThat(listener.groupId())
                .isEqualTo("analytics-group");
    }

    private KafkaProperties kafkaProperties() {
        KafkaProperties properties =
                new KafkaProperties();

        properties.setBootstrapServers(
                List.of("kafka:29092")
        );
        properties.getConsumer().setGroupId(
                "analytics-group"
        );
        properties.getConsumer().setAutoOffsetReset(
                "earliest"
        );
        properties.getConsumer().getProperties().put(
                JsonDeserializer.TRUSTED_PACKAGES,
                TRUSTED_EVENT_PACKAGE
        );
        properties.getConsumer().getProperties().put(
                JsonDeserializer.USE_TYPE_INFO_HEADERS,
                "false"
        );
        properties.getConsumer().getProperties().put(
                JsonDeserializer.VALUE_DEFAULT_TYPE,
                FlagEvent.class.getName()
        );

        return properties;
    }
}
