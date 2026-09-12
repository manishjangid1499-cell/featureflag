package com.featureflag.flag_service.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class KafkaConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ThreadPoolExecutor evaluationTelemetryExecutor(
            @Value("${telemetry.evaluation.queue-capacity:256}") int capacity
    ) {
        if (capacity < 1 || capacity > 10000) {
            throw new IllegalArgumentException("Telemetry queue capacity must be between 1 and 10000");
        }
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity), task -> {
                    Thread thread = new Thread(task, "evaluation-telemetry");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    public ProducerFactory<String, String> outboxProducerFactory(
            KafkaProperties kafkaProperties
    ) {
        Map<String, Object> properties =
                new HashMap<>(
                        kafkaProperties.buildProducerProperties()
                );

        properties.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );
        properties.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );
        properties.put(
                ProducerConfig.ACKS_CONFIG,
                "all"
        );
        properties.put(
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
                true
        );
        // Bound metadata/buffer waits for the best-effort worker and outbox attempts.
        properties.putIfAbsent(ProducerConfig.MAX_BLOCK_MS_CONFIG, 1000);

        return new DefaultKafkaProducerFactory<>(
                properties,
                new StringSerializer(),
                new StringSerializer()
        );
    }

    @Bean
    public KafkaTemplate<String, String> outboxKafkaTemplate(
            ProducerFactory<String, String> outboxProducerFactory
    ) {
        return new KafkaTemplate<>(outboxProducerFactory);
    }
}
