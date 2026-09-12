package com.featureflag.notification_service.config;

import com.featureflag.notification_service.exception.UnsupportedNotificationChannelException;
import com.featureflag.notification_service.observability.KafkaCorrelationRecordInterceptor;
import com.featureflag.notification_service.observability.KafkaFailureVisibility;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    public static final String DLT_TOPIC =
            "notification-events-dlt";

    static final long RETRY_BACKOFF_MS = 1_000L;
    static final long MAX_RETRIES = 2L;
    static final long INFRASTRUCTURE_RETRY_BACKOFF_MS = 5_000L;
    static final long INFRASTRUCTURE_MAX_RETRIES = 12L;

    static FixedBackOff retryBackOff(Exception exception) {
        Throwable cause = exception;
        for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
            if (cause instanceof TransientDataAccessException
                    || cause instanceof DataAccessResourceFailureException
                    || cause instanceof CannotCreateTransactionException
                    || cause instanceof java.sql.SQLTransientException
                    || cause instanceof java.sql.SQLRecoverableException) {
                return new FixedBackOff(INFRASTRUCTURE_RETRY_BACKOFF_MS, INFRASTRUCTURE_MAX_RETRIES);
            }
        }
        return new FixedBackOff(RETRY_BACKOFF_MS, MAX_RETRIES);
    }

    private final KafkaProperties kafkaProperties;

    public KafkaConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> properties =
                new HashMap<>(
                        kafkaProperties
                                .buildConsumerProperties()
                );

        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );
        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );
        properties.put(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false
        );

        return new DefaultKafkaConsumerFactory<>(
                properties,
                new StringDeserializer(),
                new StringDeserializer()
        );
    }

    @Bean
    public ProducerFactory<String, String> dltProducerFactory() {
        Map<String, Object> properties =
                new HashMap<>(
                        kafkaProperties
                                .buildProducerProperties()
                );

        properties.put(
                ProducerConfig.ACKS_CONFIG,
                "all"
        );
        properties.put(
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
                true
        );

        return new DefaultKafkaProducerFactory<>(
                properties,
                new StringSerializer(),
                new StringSerializer()
        );
    }

    @Bean
    public KafkaTemplate<String, String> dltKafkaTemplate(
            ProducerFactory<String, String> dltProducerFactory
    ) {
        return new KafkaTemplate<>(dltProducerFactory);
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> dltKafkaTemplate,
            KafkaFailureVisibility failureVisibility
    ) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        dltKafkaTemplate,
                        (record, exception) ->
                                failureVisibility.dltDestination(
                                        record,
                                        exception,
                                        DLT_TOPIC
                                )
                );

        recoverer.setFailIfSendResultIsError(true);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(
                        RETRY_BACKOFF_MS,
                        MAX_RETRIES
                )
        );

        errorHandler.addNotRetryableExceptions(
                UnsupportedNotificationChannelException.class
        );
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class, JsonProcessingException.class);
        errorHandler.setBackOffFunction((record, exception) -> retryBackOff(exception));
        errorHandler.setRetryListeners(
                failureVisibility::recordFailure
        );

        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
    kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String>
                factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.setRecordInterceptor(
                new KafkaCorrelationRecordInterceptor<>()
        );
        factory.getContainerProperties().setAckMode(
                ContainerProperties.AckMode.RECORD
        );

        return factory;
    }
}
