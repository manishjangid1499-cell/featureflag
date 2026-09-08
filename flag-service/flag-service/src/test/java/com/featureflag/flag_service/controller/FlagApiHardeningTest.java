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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                "2026-09-02T10:00:00",
                "2026-09-01T10:00:00"
        ));
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
        verify(flagService, never()).getAllFlags();
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
}
