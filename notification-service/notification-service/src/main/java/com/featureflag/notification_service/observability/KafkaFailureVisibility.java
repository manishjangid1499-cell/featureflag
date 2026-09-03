package com.featureflag.notification_service.observability;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Slf4j
public class KafkaFailureVisibility {

    private static final String SERVICE = "notification-service";
    private static final Set<String> KNOWN_TOPICS =
            Set.of(
                    "notification-events"
            );

    private final MeterRegistry meterRegistry;

    public KafkaFailureVisibility(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordFailure(
            ConsumerRecord<?, ?> record,
            Exception exception,
            int deliveryAttempt
    ) {
        String topic = boundedTopic(record.topic());
        String category = exceptionCategory(exception);
        meterRegistry.counter(
                "kafka.consumer.failures",
                "service", SERVICE,
                "topic", topic,
                "category", category
        ).increment();

        log.warn(
                "Kafka consumer delivery failed; service={} topic={} partition={} offset={} attempt={} category={} correlationId={}",
                SERVICE,
                topic,
                record.partition(),
                record.offset(),
                deliveryAttempt,
                category,
                CorrelationIds.currentOrGenerate()
        );
    }

    public TopicPartition dltDestination(
            ConsumerRecord<?, ?> record,
            Exception exception,
            String dltTopic
    ) {
        String topic = boundedTopic(record.topic());
        String category = exceptionCategory(exception);
        meterRegistry.counter(
                "kafka.consumer.dlt",
                "service", SERVICE,
                "topic", topic,
                "category", category
        ).increment();

        log.error(
                "Kafka record routed to DLT; service={} topic={} dltTopic={} partition={} offset={} category={} correlationId={}",
                SERVICE,
                topic,
                dltTopic,
                record.partition(),
                record.offset(),
                category,
                CorrelationIds.currentOrGenerate()
        );

        return new TopicPartition(dltTopic, -1);
    }

    private String boundedTopic(String topic) {
        return KNOWN_TOPICS.contains(topic)
                ? topic
                : "other";
    }

    private String exceptionCategory(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null
                && current.getCause() != current) {
            current = current.getCause();
        }

        String name = current.getClass()
                .getSimpleName()
                .toLowerCase();
        if (current instanceof IllegalArgumentException) {
            return "validation";
        }
        if (name.contains("json")
                || name.contains("deserial")) {
            return "deserialization";
        }
        if (name.contains("data")
                || name.contains("sql")
                || name.contains("persist")) {
            return "persistence";
        }
        if (name.contains("feign")
                || name.contains("connect")
                || name.contains("timeout")) {
            return "downstream";
        }
        return "processing";
    }
}
