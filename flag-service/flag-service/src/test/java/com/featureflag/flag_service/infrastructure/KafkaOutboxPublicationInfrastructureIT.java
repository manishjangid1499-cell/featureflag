package com.featureflag.flag_service.infrastructure;

import com.featureflag.flag_service.entity.OutboxEvent;
import com.featureflag.flag_service.observability.CorrelationIds;
import com.featureflag.flag_service.observability.FlagMetrics;
import com.featureflag.flag_service.repository.OutboxEventRepository;
import com.featureflag.flag_service.service.OutboxDeliveryService;
import com.featureflag.flag_service.service.OutboxPayloadEnricher;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class KafkaOutboxPublicationInfrastructureIT {

    @Container
    private static final KafkaContainer KAFKA =
            new KafkaContainer(
                    DockerImageName.parse("apache/kafka:4.3.1")
            );

    @Test
    void realKafkaPreservesPayloadEventIdentityAndCorrelationHeader()
            throws Exception {
        String topic = "outbox-contract-" + UUID.randomUUID();
        createTopic(topic);

        DefaultKafkaProducerFactory<String, String> producerFactory =
                new DefaultKafkaProducerFactory<>(
                        Map.of(
                                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                                KAFKA.getBootstrapServers(),
                                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                                StringSerializer.class,
                                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                                StringSerializer.class,
                                ProducerConfig.ACKS_CONFIG,
                                "all"
                        )
                );
        KafkaTemplate<String, String> kafkaTemplate =
                new KafkaTemplate<>(producerFactory);

        Map<String, Object> consumerProperties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "outbox-contract-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );

        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(consumerProperties)) {
            consumer.subscribe(List.of(topic));

            String eventId =
                    "11111111-2222-3333-4444-555555555555";
            String correlationId =
                    "batch-c-correlation-1";
            String payload =
                    "{\"eventId\":\"" + eventId
                            + "\",\"eventType\":\"FLAG_UPDATED\"}";
            OutboxEvent event = OutboxEvent.builder()
                    .id(eventId)
                    .topic(topic)
                    .messageKey("NEW_CHECKOUT")
                    .correlationId(correlationId)
                    .eventType("FLAG_UPDATED")
                    .payload(payload)
                    .status(OutboxEvent.STATUS_PENDING)
                    .attempts(0)
                    .createdAt(Instant.now().minusSeconds(1))
                    .nextAttemptAt(Instant.now().minusSeconds(1))
                    .build();
            OutboxEventRepository repository =
                    mock(OutboxEventRepository.class);
            when(repository.findByIdForUpdate(eventId))
                    .thenReturn(Optional.of(event));
            OutboxPayloadEnricher enricher =
                    mock(OutboxPayloadEnricher.class);
            when(enricher.enrich(any(OutboxEvent.class)))
                    .thenReturn(payload);
            FlagMetrics metrics = mock(FlagMetrics.class);
            OutboxDeliveryService service =
                    new OutboxDeliveryService(
                            repository,
                            kafkaTemplate,
                            10L,
                            3,
                            Clock.systemUTC(),
                            metrics,
                            enricher
                    );

            service.publishById(eventId);

            ConsumerRecord<String, String> record =
                    awaitRecord(consumer, topic);
            assertThat(record.key()).isEqualTo("NEW_CHECKOUT");
            assertThat(record.value()).isEqualTo(payload);
            assertThat(record.value()).contains(eventId);
            assertThat(record.headers()
                    .lastHeader(CorrelationIds.HEADER_NAME))
                    .isNotNull();
            assertThat(new String(
                    record.headers()
                            .lastHeader(CorrelationIds.HEADER_NAME)
                            .value(),
                    StandardCharsets.UTF_8
            )).isEqualTo(correlationId);
            assertThat(event.getStatus())
                    .isEqualTo(OutboxEvent.STATUS_PUBLISHED);
            verify(metrics).outboxPublished(topic);
        } finally {
            producerFactory.destroy();
        }
    }

    private void createTopic(String topic) throws Exception {
        try (AdminClient adminClient = AdminClient.create(
                Map.of(
                        AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                        KAFKA.getBootstrapServers()
                )
        )) {
            adminClient.createTopics(
                    List.of(new NewTopic(topic, 1, (short) 1))
            ).all().get(20, TimeUnit.SECONDS);
        }
    }

    private ConsumerRecord<String, String> awaitRecord(
            KafkaConsumer<String, String> consumer,
            String topic
    ) {
        long deadline = System.nanoTime()
                + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> records =
                    consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if (topic.equals(record.topic())) {
                    return record;
                }
            }
        }
        throw new AssertionError(
                "Timed out waiting for Kafka record on " + topic
        );
    }
}
