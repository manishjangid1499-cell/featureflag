package com.featureflag.flag_service.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void validIncomingValueIsReturnedAndAvailableOnlyDuringRequest()
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIds.HEADER_NAME, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> duringRequest = new AtomicReference<>();

        filter.doFilter(
                request,
                response,
                (ignoredRequest, ignoredResponse) -> duringRequest.set(
                        MDC.get(CorrelationIds.MDC_KEY)
                )
        );

        assertThat(duringRequest).hasValue("request-123");
        assertThat(response.getHeader(CorrelationIds.HEADER_NAME))
                .isEqualTo("request-123");
        assertThat(MDC.get(CorrelationIds.MDC_KEY)).isNull();
    }

    @Test
    void missingDuplicateAndOversizedValuesAreReplacedSafely()
            throws Exception {
        assertReplaced(new MockHttpServletRequest());

        MockHttpServletRequest duplicate = new MockHttpServletRequest();
        duplicate.addHeader(CorrelationIds.HEADER_NAME, "one");
        duplicate.addHeader(CorrelationIds.HEADER_NAME, "two");
        assertReplaced(duplicate);

        MockHttpServletRequest oversized = new MockHttpServletRequest();
        oversized.addHeader(CorrelationIds.HEADER_NAME, "x".repeat(65));
        assertReplaced(oversized);
    }

    private void assertReplaced(MockHttpServletRequest request)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
        });

        String generated = response.getHeader(CorrelationIds.HEADER_NAME);
        assertThat(generated)
                .hasSize(36)
                .matches("[A-Za-z0-9._-]+");
    }
}
