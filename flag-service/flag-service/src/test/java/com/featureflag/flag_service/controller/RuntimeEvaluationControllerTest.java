package com.featureflag.flag_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.repository.FeatureFlagRepository;
import com.featureflag.flag_service.observability.FlagMetrics;
import com.featureflag.flag_service.security.SdkKeyPrincipal;
import com.featureflag.flag_service.service.EvaluationTelemetryPublisher;
import com.featureflag.flag_service.service.FlagEvaluationTelemetryService;
import com.featureflag.flag_service.service.FlagService;
import com.featureflag.flag_service.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeEvaluationControllerTest {

    private FeatureFlagRepository repository;
    private EvaluationTelemetryPublisher telemetryPublisher;
    private RuntimeEvaluationController controller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(FeatureFlagRepository.class);
        RedisTemplate<String, Object> redisTemplate =
                mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations =
                mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(null);

        FlagService flagService = new FlagService(
                repository,
                redisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                mock(OutboxService.class)
        );
        telemetryPublisher = mock(EvaluationTelemetryPublisher.class);
        controller = new RuntimeEvaluationController(
                new FlagEvaluationTelemetryService(
                        flagService,
                        telemetryPublisher,
                        mock(FlagMetrics.class)
                )
        );
    }

    @Test
    void sameFlagIsEvaluatedOnlyInAuthenticatedKeyEnvironment() {
        FeatureFlag development = flag("DEV", true);
        FeatureFlag production = flag("PROD", false);
        when(repository.findByFlagKeyAndEnvironment(
                "checkout",
                "DEV"
        )).thenReturn(Optional.of(development));
        when(repository.findByFlagKeyAndEnvironment(
                "checkout",
                "PROD"
        )).thenReturn(Optional.of(production));

        var response = controller.evaluate(
                "checkout",
                "target-user",
                principal("DEV")
        ).getBody();

        assertThat(response).isNotNull();
        assertThat(response.environment()).isEqualTo("DEV");
        assertThat(response.enabled()).isTrue();
        verify(repository).findByFlagKeyAndEnvironment(
                "checkout",
                "DEV"
        );
        verify(repository, never()).findByFlagKeyAndEnvironment(
                "checkout",
                "PROD"
        );
        verify(telemetryPublisher).publish(
                org.mockito.ArgumentMatchers.argThat(
                        evaluation -> evaluation.isEnabled()
                                && "DEV".equals(
                                evaluation.getEnvironment()
                        )
                )
        );
    }

    @Test
    void telemetryFailureDoesNotChangeRuntimeDecision() {
        when(repository.findByFlagKeyAndEnvironment(
                "checkout",
                "DEV"
        )).thenReturn(Optional.of(flag("DEV", true)));
        doThrow(new RuntimeException("telemetry unavailable"))
                .when(telemetryPublisher)
                .publish(org.mockito.ArgumentMatchers.any());

        var response = controller.evaluate(
                "checkout",
                "target-user",
                principal("DEV")
        ).getBody();

        assertThat(response).isNotNull();
        assertThat(response.enabled()).isTrue();
    }

    private FeatureFlag flag(
            String environment,
            boolean globallyEnabled
    ) {
        return FeatureFlag.builder()
                .id("DEV".equals(environment) ? 1L : 2L)
                .flagKey("checkout")
                .environment(environment)
                .enabled(globallyEnabled)
                .rolloutPercentage(0)
                .targetUsers(List.of("target-user"))
                .build();
    }

    private SdkKeyPrincipal principal(String environment) {
        return new SdkKeyPrincipal(
                10L,
                "Runtime key",
                environment,
                "ff_sdk_AAAAAAAA"
        );
    }
}
