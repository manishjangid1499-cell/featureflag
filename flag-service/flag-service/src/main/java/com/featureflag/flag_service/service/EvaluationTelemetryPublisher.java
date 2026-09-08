package com.featureflag.flag_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import com.featureflag.flag_service.event.FlagEvent;
import com.featureflag.flag_service.observability.CorrelationIds;
import com.featureflag.flag_service.observability.FlagMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.MDC;

@Service
@Slf4j
public class EvaluationTelemetryPublisher {

    public static final String DEFAULT_TOPIC =
            "feature-flag-evaluations";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final Clock clock;
    private final FlagMetrics flagMetrics;
    private final Executor executor;

    public EvaluationTelemetryPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value(
                    "${telemetry.evaluation.topic:"
                            + DEFAULT_TOPIC
                            + "}"
            )
            String topic,
            Clock clock,
            FlagMetrics flagMetrics,
            @Qualifier("evaluationTelemetryExecutor") Executor executor
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.clock = clock;
        this.flagMetrics = flagMetrics;
        this.executor = executor;
    }

    public void publish(FlagEvaluationResponse evaluation) {
        String eventId = UUID.randomUUID().toString();
        FlagEvent event = new FlagEvent(
                eventId,
                evaluation.isEnabled()
                        ? FlagEvent.EVALUATION_ENABLED
                        : FlagEvent.EVALUATION_DISABLED,
                evaluation.getFlagKey(),
                evaluation.getEnvironment(),
                clock.instant().toString()
        );

        String correlationId = CorrelationIds.currentOrGenerate();
        try {
            executor.execute(() -> publishAdmitted(event, correlationId));
            flagMetrics.telemetryAdmitted(true);
        } catch (RejectedExecutionException exception) {
            flagMetrics.telemetryAdmitted(false);
            log.debug("Evaluation telemetry dropped; queue full or shutting down");
        }
    }

    private void publishAdmitted(FlagEvent event, String correlationId) {
        String previous = MDC.get(CorrelationIds.MDC_KEY);
        MDC.put(CorrelationIds.MDC_KEY, correlationId);
        try {
            send(event, correlationId);
        } catch (RuntimeException exception) {
            flagMetrics.telemetryPublished(false);
            log.warn("Evaluation telemetry worker failed; eventId={} errorType={}",
                    event.getEventId(), exception.getClass().getSimpleName());
        } finally {
            if (previous == null) {
                MDC.remove(CorrelationIds.MDC_KEY);
            } else {
                MDC.put(CorrelationIds.MDC_KEY, previous);
            }
        }
    }

    private void send(FlagEvent event, String correlationId) {
        String eventId = event.getEventId();

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            flagMetrics.telemetryPublished(false);
            log.warn(
                    "Evaluation telemetry serialization failed; "
                            + "eventId={} flagKey={} environment={} "
                            + "errorType={}",
                    eventId,
                    event.getFlagKey(),
                    event.getEnvironment(),
                    exception.getClass().getSimpleName()
            );
            return;
        }

        String messageKey = event.getFlagKey()
                + ":"
                + event.getEnvironment();

        ProducerRecord<String, String> record =
                new ProducerRecord<>(
                        topic,
                        messageKey,
                        payload
                );
        record.headers().add(
                CorrelationIds.HEADER_NAME,
                correlationId.getBytes(StandardCharsets.UTF_8)
        );

        try {
            kafkaTemplate.send(
                    record
            ).whenComplete((result, exception) -> {
                if (exception != null) {
                    flagMetrics.telemetryPublished(false);
                    log.warn(
                            "Evaluation telemetry publish failed; "
                                    + "eventId={} flagKey={} "
                                    + "environment={} errorType={} "
                                    + "correlationId={}",
                            eventId,
                            event.getFlagKey(),
                            event.getEnvironment(),
                            exception.getClass().getSimpleName(),
                            correlationId
                    );
                } else {
                    flagMetrics.telemetryPublished(true);
                }
            });
        } catch (RuntimeException exception) {
            flagMetrics.telemetryPublished(false);
            log.warn(
                    "Evaluation telemetry enqueue failed; "
                            + "eventId={} flagKey={} environment={} "
                            + "errorType={} correlationId={}",
                    eventId,
                    event.getFlagKey(),
                    event.getEnvironment(),
                    exception.getClass().getSimpleName(),
                    correlationId
            );
        }
    }
}
