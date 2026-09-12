package com.featureflag.notification_service.exception;

import com.featureflag.notification_service.observability.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiErrorContractTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ContractController())
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemDetails(
                        Jackson2ObjectMapperBuilder.json().build())))
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void malformedJsonAndInvalidParametersAre400WithoutEchoingInput() throws Exception {
        mvc.perform(post("/contract/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"PRIVATE_VALUE\","))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().string(not(containsString("PRIVATE_VALUE"))));
        mvc.perform(get("/contract/number"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/contract/number").param("page", "PRIVATE_VALUE"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("PRIVATE_VALUE"))));
    }

    @Test
    void validationIdentifiesFieldsWithoutRejectedSecrets() throws Exception {
        mvc.perform(post("/contract/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"password\":\"PRIVATE_VALUE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("must not be blank"))
                .andExpect(content().string(not(containsString("PRIVATE_VALUE"))))
                .andExpect(jsonPath("$.code").value("validation-failed"));
    }

    @Test
    void unauthenticatedAndForbiddenKeepTheirStatusAndSafeDetails() throws Exception {
        mvc.perform(get("/contract/error/401"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(content().string(not(containsString("PRIVATE_VALUE"))));
        mvc.perform(get("/contract/error/403"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(content().string(not(containsString("PRIVATE_VALUE"))));
    }

    @Test
    void missingResourceIs404() throws Exception {
        mvc.perform(get("/contract/error/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:feature-flag-platform:problem:resource-not-found"));
    }

    @Test
    void unexpectedFailureIsSanitizedAndCorrelatedWithoutQuerySecrets() throws Exception {
        mvc.perform(get("/contract/error/500").param("token", "PRIVATE_VALUE")
                        .header("X-Correlation-ID", "contract-test-123"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Correlation-ID", "contract-test-123"))
                .andExpect(jsonPath("$.correlationId").value("contract-test-123"))
                .andExpect(jsonPath("$.instance").value("/contract/error/500"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("PRIVATE_VALUE"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void unsupportedMediaTypeAndMethodKeepFrameworkStatusAndHeaders() throws Exception {
        mvc.perform(post("/contract/body").contentType(MediaType.TEXT_PLAIN).content("PRIVATE_VALUE"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(delete("/contract/number"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")));
    }

    @RestController
    static class ContractController {
        @GetMapping("/contract/number")
        int number(@RequestParam int page) { return page; }

        @PostMapping("/contract/body")
        void body(@Valid @RequestBody ContractRequest request) { }

        @GetMapping("/contract/error/{status}")
        void error(@PathVariable int status) {
            switch (status) {
                case 401 -> throw new BadCredentialsException("PRIVATE_VALUE");
                case 403 -> throw new AccessDeniedException("PRIVATE_VALUE");
                case 404 -> throw new ResourceNotFoundException("PRIVATE_VALUE");
                default -> throw new IllegalStateException("SQL password=PRIVATE_VALUE");
            }
        }
    }

    record ContractRequest(@NotBlank String name, String password) { }
}
