package com.featureflag.flag_service.controller;

import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import com.featureflag.flag_service.service.FlagEvaluationTelemetryService;
import com.featureflag.flag_service.service.FlagMutationAuditService;
import com.featureflag.flag_service.service.FlagQueryService;
import com.featureflag.flag_service.service.FlagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlagControllerEvaluationTelemetryTest {

    @Mock
    private FlagService flagService;

    @Mock
    private FlagEvaluationTelemetryService
            flagEvaluationTelemetryService;

    @Mock
    private FlagMutationAuditService flagMutationAuditService;

    @Mock
    private FlagQueryService flagQueryService;

    @InjectMocks
    private FlagController controller;

    @Test
    void evaluationEndpointUsesTelemetryAwareEvaluationPath() {
        FlagEvaluationResponse evaluation =
                new FlagEvaluationResponse(
                        "checkout",
                        "DEV",
                        true,
                        false,
                        100,
                        null,
                        null,
                        true
                );
        when(
                flagEvaluationTelemetryService.evaluateFlag(
                        "checkout",
                        "user-1",
                        "DEV"
                )
        ).thenReturn(evaluation);

        ResponseEntity<FlagEvaluationResponse> response =
                controller.evaluateFlag(
                        "checkout",
                        "user-1",
                        "DEV"
                );

        assertSame(evaluation, response.getBody());
        verify(flagEvaluationTelemetryService).evaluateFlag(
                "checkout",
                "user-1",
                "DEV"
        );
        verifyNoInteractions(flagService);
    }
}
