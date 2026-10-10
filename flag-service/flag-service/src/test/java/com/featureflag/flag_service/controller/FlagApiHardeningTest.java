package com.featureflag.flag_service.controller;

import com.featureflag.flag_service.config.TimeConfiguration;
import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.service.FlagEvaluationTelemetryService;
import com.featureflag.flag_service.service.FlagMutationAuditService;
import com.featureflag.flag_service.service.FlagQueryService;
import com.featureflag.flag_service.service.FlagService;
import com.featureflag.flag_service.service.SdkKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.featureflag.flag_service.dto.FlagRequest;
import com.featureflag.flag_service.dto.FlagToggleRequest;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        FlagController.class,
        SdkKeyController.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(TimeConfiguration.class)
class FlagApiHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FlagService flagService;

    @MockitoBean
    private FlagEvaluationTelemetryService evaluationService;

    @MockitoBean
    private FlagMutationAuditService mutationService;

    @MockitoBean
    private FlagQueryService queryService;

    @MockitoBean
    private SdkKeyService sdkKeyService;

    @Test
    void blankRequiredFlagValueIsRejectedBeforeMutation() throws Exception {
        performInvalidFlag("""
                {
                  "name":" ",
                  "flagKey":"checkout",
                  "enabled":true,
                  "environment":"DEV",
                  "rolloutPercentage":50
                }
                """);
        performInvalidFlag("""
                {
                  "name":"Checkout",
                  "flagKey":"unsafe key",
                  "enabled":true,
                  "environment":"DEV",
                  "rolloutPercentage":50
                }
                """);
        performInvalidFlag("""
                {
                  "name":"Checkout",
                  "flagKey":"checkout",
                  "enabled":true,
                  "environment":"LOCAL",
                  "rolloutPercentage":50
                }
                """);
    }

    @Test
    void rolloutOutsideZeroToOneHundredIsRejected() throws Exception {
        performInvalidFlag(flagJson(-1, null, null));
        performInvalidFlag(flagJson(101, null, null));
    }

    @Test
    void endBeforeStartIsRejected() throws Exception {
        performInvalidFlag(flagJson(
                50,
                "2026-09-02T10:00:00Z",
                "2026-09-01T10:00:00Z"
        ));
    }

    @Test
    void ambiguousTimezoneLessScheduleIsRejectedWithBadRequest() throws Exception {
        mockMvc.perform(
                post("/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(flagJson(50, "2026-09-02T10:00:00", null))
        )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"));
        verifyNoInteractions(mutationService);
    }

    @Test
    void invalidDateScheduleIsRejectedWithBadRequest() throws Exception {
        mockMvc.perform(
                post("/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(flagJson(50, "2026-02-31T10:00:00Z", null))
        )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"));
        verifyNoInteractions(mutationService);
    }

    @Test
    void invalidAndOversizedPagesAreRejectedBeforeQuery() throws Exception {
        for (String query : List.of(
                "?page=-1",
                "?size=0",
                "?size=101"
        )) {
            mockMvc.perform(get("/flags" + query))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(queryService);
    }

    @Test
    void sdkKeyMetadataIsValidatedBeforeIssuance() throws Exception {
        mockMvc.perform(
                post("/sdk-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":" ",
                                  "environment":"DEV"
                                }
                                """)
        ).andExpect(status().isBadRequest());

        mockMvc.perform(
                post("/sdk-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Backend",
                                  "environment":"LOCAL"
                                }
                                """)
        ).andExpect(status().isBadRequest());

        verifyNoInteractions(sdkKeyService);
    }

    @Test
    void flagsUseBoundedDatabasePageAndExplicitDto() throws Exception {
        FeatureFlag flag = FeatureFlag.builder()
                .id(9L)
                .version(7L)
                .name("Checkout")
                .flagKey("checkout")
                .description("New checkout")
                .environment("DEV")
                .enabled(true)
                .rolloutPercentage(50)
                .targetUsers(List.of("user-1"))
                .build();
        Pageable pageable = org.springframework.data.domain.PageRequest.of(
                0,
                20
        );
        when(queryService.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(flag), pageable, 1));

        mockMvc.perform(get("/flags?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(9))
                .andExpect(jsonPath("$.content[0].flagKey")
                        .value("checkout"))
                .andExpect(jsonPath("$.content[0].version")
                        .value(7))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(queryService).findAll(any(Pageable.class));
        verifyNoInteractions(flagService);
    }

    private void performInvalidFlag(String json) throws Exception {
        mockMvc.perform(
                post("/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors").isMap());
        verifyNoInteractions(mutationService);
    }

    private String flagJson(
            int rolloutPercentage,
            String startDate,
            String endDate
    ) {
        String start = startDate == null
                ? "null"
                : "\"" + startDate + "\"";
        String end = endDate == null
                ? "null"
                : "\"" + endDate + "\"";
        return """
                {
                  "name":"Checkout",
                  "flagKey":"checkout",
                  "enabled":true,
                  "environment":"DEV",
                  "rolloutPercentage":%d,
                  "startDate":%s,
                  "endDate":%s
                }
                """.formatted(rolloutPercentage, start, end);
    }

    @Test
    void bodylessToggleRequestIsRejectedWithBadRequest() throws Exception {
        mockMvc.perform(
                patch("/flags/1/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
        )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(mutationService);
    }

    @Test
    void toggleWithMissingFieldsIsRejectedWithBadRequest() throws Exception {
        mockMvc.perform(
                patch("/flags/1/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
        )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.enabled").exists())
                .andExpect(jsonPath("$.errors.expectedVersion").exists());
        verifyNoInteractions(mutationService);
    }

    @Test
    void toggleWithNegativeExpectedVersionIsRejectedWithBadRequest() throws Exception {
        mockMvc.perform(
                patch("/flags/1/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "expectedVersion": -1
                                }
                                """)
        )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.expectedVersion").exists());
        verifyNoInteractions(mutationService);
    }

    @Test
    void toggleWithStaleVersionReturnsConflict409() throws Exception {
        when(mutationService.toggleFlag(
                org.mockito.ArgumentMatchers.eq(1L),
                any(FlagToggleRequest.class),
                any()
        )).thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(FeatureFlag.class, 1L));

        mockMvc.perform(
                patch("/flags/1/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "expectedVersion": 0
                                }
                                """)
        )
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Feature flag was modified by another request"));
    }

    @Test
    void toggleWithValidPayloadSucceeds() throws Exception {
        FeatureFlag flag = FeatureFlag.builder()
                .id(1L)
                .version(1L)
                .name("Checkout")
                .flagKey("checkout")
                .enabled(true)
                .environment("DEV")
                .rolloutPercentage(50)
                .build();
        when(mutationService.toggleFlag(
                org.mockito.ArgumentMatchers.eq(1L),
                any(FlagToggleRequest.class),
                any()
        )).thenReturn(flag);

        mockMvc.perform(
                patch("/flags/1/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "expectedVersion": 0
                                }
                                """)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void updateWithSameIdentitySucceeds() throws Exception {
        FeatureFlag flag = FeatureFlag.builder()
                .id(1L)
                .version(2L)
                .name("Updated Checkout")
                .flagKey("checkout")
                .enabled(true)
                .environment("DEV")
                .rolloutPercentage(75)
                .build();
        when(mutationService.updateFlag(
                org.mockito.ArgumentMatchers.eq(1L),
                any(FlagRequest.class),
                any()
        )).thenReturn(flag);

        mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/flags/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion": 1,
                                  "name": "Updated Checkout",
                                  "flagKey": "checkout",
                                  "environment": "DEV",
                                  "enabled": true,
                                  "rolloutPercentage": 75
                                }
                                """)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.name").value("Updated Checkout"))
                .andExpect(jsonPath("$.flagKey").value("checkout"))
                .andExpect(jsonPath("$.environment").value("DEV"));
    }

    @Test
    void updateWithChangedKeyIsRejectedWithBadRequest400() throws Exception {
        when(mutationService.updateFlag(
                org.mockito.ArgumentMatchers.eq(1L),
                any(FlagRequest.class),
                any()
        )).thenThrow(new com.featureflag.flag_service.exception.InvalidOperationException("Flag key cannot be changed after creation"));

        mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/flags/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion": 1,
                                  "name": "Updated Checkout",
                                  "flagKey": "new_key",
                                  "environment": "DEV",
                                  "enabled": true,
                                  "rolloutPercentage": 75
                                }
                                """)
        )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid Operation"))
                .andExpect(jsonPath("$.detail").value("Flag key cannot be changed after creation"));
    }

    @Test
    void updateWithChangedEnvironmentIsRejectedWithBadRequest400() throws Exception {
        when(mutationService.updateFlag(
                org.mockito.ArgumentMatchers.eq(1L),
                any(FlagRequest.class),
                any()
        )).thenThrow(new com.featureflag.flag_service.exception.InvalidOperationException("Flag environment cannot be changed after creation"));

        mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/flags/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion": 1,
                                  "name": "Updated Checkout",
                                  "flagKey": "checkout",
                                  "environment": "PROD",
                                  "enabled": true,
                                  "rolloutPercentage": 75
                                }
                                """)
        )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid Operation"))
                .andExpect(jsonPath("$.detail").value("Flag environment cannot be changed after creation"));
    }

    @Test
    void updateWithStaleVersionReturnsConflict409() throws Exception {
        when(mutationService.updateFlag(
                org.mockito.ArgumentMatchers.eq(1L),
                any(FlagRequest.class),
                any()
        )).thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(FeatureFlag.class, 1L));

        mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/flags/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion": 0,
                                  "name": "Updated Checkout",
                                  "flagKey": "checkout",
                                  "environment": "DEV",
                                  "enabled": true,
                                  "rolloutPercentage": 75
                                }
                                """)
        )
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Feature flag was modified by another request"));
    }
}
