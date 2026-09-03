package com.featureflag.flag_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.exception.ResourceNotFoundException;
import com.featureflag.flag_service.repository.FeatureFlagRepository;
import com.featureflag.flag_service.observability.FlagMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import io.micrometer.core.instrument.Timer;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlagEvaluationTelemetryServiceTest {

    @Mock
    private FlagService flagService;

    @Mock
    private EvaluationTelemetryPublisher telemetryPublisher;

    @Mock
    private FlagMetrics flagMetrics;

    @InjectMocks
    private FlagEvaluationTelemetryService service;

    @Test
    void enabledEvaluationPublishesExactlyOnce() {
        FlagEvaluationResponse evaluation = evaluation(true);
        when(
                flagService.evaluateFlag(
                        "checkout",
                        "user-1",
                        "DEV"
                )
        ).thenReturn(evaluation);

        FlagEvaluationResponse result = service.evaluateFlag(
                "checkout",
                "user-1",
                "DEV"
        );

        assertSame(evaluation, result);
        assertTrue(result.isEnabled());
        verify(telemetryPublisher, times(1)).publish(evaluation);
        verify(flagMetrics).evaluationCompleted(
                org.mockito.ArgumentMatchers.<Timer.Sample>any(),
                org.mockito.ArgumentMatchers.eq(true)
        );
    }

    @Test
    void rolloutExcludedEvaluationPublishesDisabledOutcome() {
        FlagEvaluationResponse evaluation = evaluation(false);
        when(
                flagService.evaluateFlag(
                        "checkout",
                        "excluded-user",
                        "DEV"
                )
        ).thenReturn(evaluation);

        FlagEvaluationResponse result = service.evaluateFlag(
                "checkout",
                "excluded-user",
                "DEV"
        );

        assertSame(evaluation, result);
        assertFalse(result.isEnabled());
        verify(telemetryPublisher, times(1)).publish(evaluation);
    }

    @Test
    void failedEvaluationDoesNotPublishSuccessfulTelemetry() {
        when(
                flagService.evaluateFlag(
                        "missing",
                        "user-1",
                        "DEV"
                )
        ).thenThrow(new ResourceNotFoundException("Flag not found"));

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.evaluateFlag(
                        "missing",
                        "user-1",
                        "DEV"
                )
        );

        verify(telemetryPublisher, never()).publish(any());
        verify(flagMetrics).evaluationFailed(
                org.mockito.ArgumentMatchers.<Timer.Sample>any()
        );
    }

    @Test
    void unexpectedTelemetryFailureDoesNotChangeEvaluationResult() {
        FlagEvaluationResponse evaluation = evaluation(true);
        when(
                flagService.evaluateFlag(
                        "checkout",
                        "user-1",
                        "DEV"
                )
        ).thenReturn(evaluation);
        org.mockito.Mockito.doThrow(
                new RuntimeException("telemetry unavailable")
        ).when(telemetryPublisher).publish(evaluation);

        FlagEvaluationResponse result = service.evaluateFlag(
                "checkout",
                "user-1",
                "DEV"
        );

        assertSame(evaluation, result);
        assertTrue(result.isEnabled());
    }

    @Test
    void redisCacheHitStillPublishesTelemetry() throws Exception {
        FeatureFlagRepository repository =
                mock(FeatureFlagRepository.class);
        RedisTemplate<String, Object> redisTemplate =
                mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations =
                mock(ValueOperations.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        OutboxService outboxService = mock(OutboxService.class);
        EvaluationTelemetryPublisher publisher =
                mock(EvaluationTelemetryPublisher.class);
        FlagMetrics metrics = mock(FlagMetrics.class);

        FeatureFlag cachedFlag = FeatureFlag.builder()
                .id(10L)
                .flagKey("cached-checkout")
                .environment("DEV")
                .enabled(true)
                .rolloutPercentage(100)
                .targetUsers(List.of())
                .build();
        String cacheKey = "flag:config:DEV:cached-checkout";
        String cachedJson = "{\"flagKey\":\"cached-checkout\"}";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(cachedJson);
        when(
                objectMapper.readValue(
                        cachedJson,
                        FeatureFlag.class
                )
        ).thenReturn(cachedFlag);

        FlagService cachedFlagService = new FlagService(
                repository,
                redisTemplate,
                objectMapper,
                outboxService
        );
        FlagEvaluationTelemetryService cachedEvaluationService =
                new FlagEvaluationTelemetryService(
                        cachedFlagService,
                        publisher,
                        metrics
                );

        FlagEvaluationResponse result =
                cachedEvaluationService.evaluateFlag(
                        "cached-checkout",
                        "user-1",
                        "DEV"
                );

        assertTrue(result.isEnabled());
        verify(repository, never())
                .findByFlagKeyAndEnvironment(
                        anyString(),
                        anyString()
                );
        verify(publisher, times(1)).publish(result);
    }

    private FlagEvaluationResponse evaluation(boolean enabled) {
        return new FlagEvaluationResponse(
                "checkout",
                "DEV",
                enabled,
                false,
                enabled ? 100 : 0,
                LocalDateTime.parse("2026-08-27T10:00:00"),
                LocalDateTime.parse("2026-08-28T10:00:00"),
                true
        );
    }
}
