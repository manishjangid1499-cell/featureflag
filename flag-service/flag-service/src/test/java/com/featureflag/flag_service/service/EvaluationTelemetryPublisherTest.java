package com.featureflag.flag_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import com.featureflag.flag_service.event.FlagEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvaluationTelemetryPublisherTest {

    private static final Instant NOW =
            Instant.parse("2026-08-27T12:00:00Z");

    private static final String TOPIC =
            "feature-flag-evaluations";

    private final KafkaTemplate<String, String> kafkaTemplate =
            mock(KafkaTemplate.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EvaluationTelemetryPublisher publisher =
            new EvaluationTelemetryPublisher(
                    kafkaTemplate,
                    objectMapper,
                    TOPIC,
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );

    @Test
    void enabledEvaluationSerializesCompleteContractAndUsesStableKey()
            throws Exception {
        when(
                kafkaTemplate.send(
                        anyString(),
                        anyString(),
                        anyString()
                )
        ).thenReturn(successfulSend());

        publisher.publish(evaluation(true));

        org.mockito.ArgumentCaptor<String> payload =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(
                org.mockito.ArgumentMatchers.eq(TOPIC),
                org.mockito.ArgumentMatchers.eq("checkout:DEV"),
                payload.capture()
        );

        FlagEvent event = objectMapper.readValue(
                payload.getValue(),
                FlagEvent.class
        );
        assertNotNull(UUID.fromString(event.getEventId()));
        assertEquals(
                FlagEvent.EVALUATION_ENABLED,
                event.getEventType()
        );
        assertEquals("checkout", event.getFlagKey());
        assertEquals("DEV", event.getEnvironment());
        assertFalse(event.getTimestamp().isBlank());
        assertEquals(NOW.toString(), event.getTimestamp());
    }

    @Test
    void disabledEvaluationUsesDisabledEventType() throws Exception {
        when(
                kafkaTemplate.send(
                        anyString(),
                        anyString(),
                        anyString()
                )
        ).thenReturn(successfulSend());

        publisher.publish(evaluation(false));

        org.mockito.ArgumentCaptor<String> payload =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(
                org.mockito.ArgumentMatchers.eq(TOPIC),
                org.mockito.ArgumentMatchers.eq("checkout:DEV"),
                payload.capture()
        );
        FlagEvent event = objectMapper.readValue(
                payload.getValue(),
                FlagEvent.class
        );
        assertEquals(
                FlagEvent.EVALUATION_DISABLED,
                event.getEventType()
        );
    }

    @Test
    void synchronousKafkaFailureIsIsolated() {
        when(
                kafkaTemplate.send(
                        anyString(),
                        anyString(),
                        anyString()
                )
        ).thenThrow(new RuntimeException("broker unavailable"));

        assertDoesNotThrow(() -> publisher.publish(evaluation(true)));
    }

    @Test
    void asynchronousKafkaFailureIsIsolated() {
        CompletableFuture<SendResult<String, String>> failure =
                new CompletableFuture<>();
        failure.completeExceptionally(
                new RuntimeException("broker rejected send")
        );
        when(
                kafkaTemplate.send(
                        anyString(),
                        anyString(),
                        anyString()
                )
        ).thenReturn(failure);

        assertDoesNotThrow(() -> publisher.publish(evaluation(false)));
    }

    @Test
    void serializationFailureIsIsolatedBeforeKafkaSend()
            throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        EvaluationTelemetryPublisher failingPublisher =
                new EvaluationTelemetryPublisher(
                        kafkaTemplate,
                        failingMapper,
                        TOPIC,
                        Clock.fixed(NOW, ZoneOffset.UTC)
                );
        when(failingMapper.writeValueAsString(
                org.mockito.ArgumentMatchers.any(FlagEvent.class)
        )).thenThrow(new JsonProcessingException("serialization failed") {
        });

        assertDoesNotThrow(
                () -> failingPublisher.publish(evaluation(true))
        );
        verify(kafkaTemplate, never()).send(
                anyString(),
                anyString(),
                anyString()
        );
    }

    private CompletableFuture<SendResult<String, String>> successfulSend() {
        return CompletableFuture.completedFuture(
                mock(SendResult.class)
        );
    }

    private FlagEvaluationResponse evaluation(boolean enabled) {
        return new FlagEvaluationResponse(
                "checkout",
                "DEV",
                enabled,
                false,
                enabled ? 100 : 0,
                null,
                null,
                true
        );
    }
}
