package com.featureflag.analytics_service.config;

import com.featureflag.analytics_service.event.FlagEvent;
import com.featureflag.analytics_service.kafka.AnalyticsEventConsumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConfigTest {

    @Test
    void consumerFactoryUsesBoundKafkaConsumerProperties() {
        KafkaProperties properties = new KafkaProperties();
        properties.setBootstrapServers(List.of("kafka:29092"));
        properties.getConsumer().setGroupId("analytics-group");
        properties.getConsumer().setAutoOffsetReset("earliest");

        DefaultKafkaConsumerFactory<String, FlagEvent> factory =
                (DefaultKafkaConsumerFactory<String, FlagEvent>)
                        new KafkaConfig(properties).consumerFactory();

        Map<String, Object> configuration = factory.getConfigurationProperties();

        assertThat(configuration.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG))
                .isEqualTo(List.of("kafka:29092"));
        assertThat(configuration)
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "analytics-group")
                .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        assertThat(factory.getKeyDeserializer()).isInstanceOf(StringDeserializer.class);
        assertThat(factory.getValueDeserializer()).isInstanceOf(JsonDeserializer.class);
    }

    @Test
    void listenerGroupIdRemainsAnalyticsGroup() throws NoSuchMethodException {
        KafkaListener listener = AnalyticsEventConsumer.class
                .getMethod("consume", FlagEvent.class)
                .getAnnotation(KafkaListener.class);

        assertThat(listener).isNotNull();
        assertThat(listener.groupId()).isEqualTo("analytics-group");
    }
}
