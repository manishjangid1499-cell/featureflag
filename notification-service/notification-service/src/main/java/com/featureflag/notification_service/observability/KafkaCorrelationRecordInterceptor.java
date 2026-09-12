package com.featureflag.notification_service.observability;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;

public class KafkaCorrelationRecordInterceptor<K, V>
        implements RecordInterceptor<K, V> {

    @Override
    public ConsumerRecord<K, V> intercept(
            ConsumerRecord<K, V> record,
            Consumer<K, V> consumer
    ) {
        MDC.put(
                CorrelationIds.MDC_KEY,
                correlationId(record)
        );
        return record;
    }

    @Override
    public void afterRecord(
            ConsumerRecord<K, V> record,
            Consumer<K, V> consumer
    ) {
        MDC.remove(CorrelationIds.MDC_KEY);
    }

    private String correlationId(ConsumerRecord<K, V> record) {
        Header header = record.headers().lastHeader(
                CorrelationIds.HEADER_NAME
        );
        if (header == null
                || header.value() == null
                || header.value().length > CorrelationIds.MAX_LENGTH) {
            return CorrelationIds.resolve(null);
        }

        return CorrelationIds.resolve(
                new String(
                        header.value(),
                        StandardCharsets.UTF_8
                )
        );
    }
}
