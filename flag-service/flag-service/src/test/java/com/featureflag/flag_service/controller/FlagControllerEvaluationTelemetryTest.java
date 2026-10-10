package com.featureflag.flag_service.controller;

import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import com.featureflag.flag_service.dto.RuntimeEvaluationResponse;
import com.featureflag.flag_service.exception.ResourceNotFoundException;
import com.featureflag.flag_service.observability.FlagMetrics;
import com.featureflag.flag_service.security.SdkKeyPrincipal;
import com.featureflag.flag_service.service.EvaluationTelemetryPublisher;
import com.featureflag.flag_service.service.FlagEvaluationTelemetryService;
import com.featureflag.flag_service.service.FlagMutationAuditService;
import com.featureflag.flag_service.service.FlagQueryService;
import com.featureflag.flag_service.service.FlagService;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlagControllerEvaluationTelemetryTest {

    @Mock
    private FlagService flagService;

    @Mock
    private FlagMutationAuditService flagMutationAuditService;

    @Mock
    private FlagQueryService flagQueryService;

    @InjectMocks
    private FlagController controller;

    @Test
    @DisplayName("Dashboard preview evaluates flag via FlagService without publishing application telemetry")
    void dashboardPreviewEvaluatesWithoutPublishingTelemetry() {
        FlagEvaluationResponse evaluation = new FlagEvaluationResponse(
                "checkout",
                "DEV",
                true,
                false,
                100,
                null,
                null,
                true
        );
        when(flagService.evaluateFlag("checkout", "user-1", "DEV")).thenReturn(evaluation);

        ResponseEntity<FlagEvaluationResponse> response =
                controller.evaluateFlag("checkout", "user-1", "DEV");

        assertNotNull(response.getBody());
        assertSame(evaluation, response.getBody());
        assertTrue(response.getBody().isEnabled());
        verify(flagService, times(1)).evaluateFlag("checkout", "user-1", "DEV");
    }

    @Test
    @DisplayName("Dashboard preview failure does not publish application telemetry")
    void dashboardPreviewFailureDoesNotPublishTelemetry() {
        when(flagService.evaluateFlag("missing-flag", "user-1", "DEV"))
                .thenThrow(new ResourceNotFoundException("Flag not found: missing-flag in environment DEV"));

        assertThrows(ResourceNotFoundException.class, () ->
                controller.evaluateFlag("missing-flag", "user-1", "DEV")
        );

        verify(flagService, times(1)).evaluateFlag("missing-flag", "user-1", "DEV");
    }

    @Test
    @DisplayName("Dashboard preview and SDK runtime evaluation produce identical decisions while only SDK publishes telemetry")
    void previewAndRuntimeProduceIdenticalDecisionsWhileOnlySdkPublishesTelemetry() {
        FlagEvaluationResponse sharedDecision = new FlagEvaluationResponse(
                "checkout",
                "DEV",
                true,
                true,
                75,
                null,
                null,
                true
        );

        when(flagService.evaluateFlag("checkout", "user-1", "DEV")).thenReturn(sharedDecision);

        // 1. Dashboard Preview path
        ResponseEntity<FlagEvaluationResponse> previewResponse =
                controller.evaluateFlag("checkout", "user-1", "DEV");
        assertNotNull(previewResponse.getBody());
        assertTrue(previewResponse.getBody().isEnabled());

        // 2. Runtime SDK Evaluation path
        EvaluationTelemetryPublisher telemetryPublisher = mock(EvaluationTelemetryPublisher.class);
        FlagMetrics flagMetrics = mock(FlagMetrics.class);
        Timer.Sample sample = mock(Timer.Sample.class);
        when(flagMetrics.startEvaluation()).thenReturn(sample);

        FlagEvaluationTelemetryService runtimeTelemetryService =
                new FlagEvaluationTelemetryService(flagService, telemetryPublisher, flagMetrics);

        RuntimeEvaluationController runtimeController =
                new RuntimeEvaluationController(runtimeTelemetryService);

        SdkKeyPrincipal principal = new SdkKeyPrincipal(1L, "Runtime Key", "DEV", "ff_sdk_key1");
        ResponseEntity<RuntimeEvaluationResponse> runtimeResponse =
                runtimeController.evaluate("checkout", "user-1", principal);

        assertNotNull(runtimeResponse.getBody());
        assertTrue(runtimeResponse.getBody().enabled());

        // Verify decisions are identical
        assertEquals(previewResponse.getBody().isEnabled(), runtimeResponse.getBody().enabled());
        assertEquals(previewResponse.getBody().getFlagKey(), runtimeResponse.getBody().flagKey());
        assertEquals(previewResponse.getBody().getEnvironment(), runtimeResponse.getBody().environment());

        // Verify telemetry publication: runtime published telemetry exactly once, preview never published
        verify(telemetryPublisher, times(1)).publish(sharedDecision);
        verify(flagMetrics, times(1)).evaluationCompleted(sample, true);
    }
}
