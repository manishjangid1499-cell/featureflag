package com.featureflag.analytics_service.observability;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaCorrelationRecordInterceptorTest {

    private final KafkaCorrelationRecordInterceptor<String, String> interceptor =
            new KafkaCorrelationRecordInterceptor<>();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void kafkaHeaderPopulatesMdcAndIsAlwaysClearedAfterRecord() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "feature-flag-events",
                0,
                10,
                "key",
                "value"
        );
        record.headers().add(
                CorrelationIds.HEADER_NAME,
                "operation-77".getBytes(StandardCharsets.UTF_8)
        );

        interceptor.intercept(record, null);
        assertThat(MDC.get(CorrelationIds.MDC_KEY))
                .isEqualTo("operation-77");

        interceptor.afterRecord(record, null);
        assertThat(MDC.get(CorrelationIds.MDC_KEY)).isNull();
    }

    @Test
    void invalidKafkaHeaderIsReplacedWithBoundedSafeValue() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "feature-flag-events",
                0,
                10,
                "key",
                "value"
        );
        record.headers().add(
                CorrelationIds.HEADER_NAME,
                "x".repeat(65).getBytes(StandardCharsets.UTF_8)
        );

        interceptor.intercept(record, null);

        assertThat(MDC.get(CorrelationIds.MDC_KEY)).hasSize(36);
        interceptor.afterRecord(record, null);
    }
}
