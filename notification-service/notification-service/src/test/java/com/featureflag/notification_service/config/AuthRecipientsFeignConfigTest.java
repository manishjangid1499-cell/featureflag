package com.featureflag.notification_service.config;

import com.featureflag.notification_service.observability.CorrelationIds;
import feign.Request;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Retryer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.assertj.core.api.Assertions.assertThat;

class AuthRecipientsFeignConfigTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void recipientClientInterceptorAddsInternalHeader() {
        AuthRecipientsFeignConfig config =
                new AuthRecipientsFeignConfig(
                        "test-recipients-key",
                        123,
                        456
                );
        RequestInterceptor interceptor = config.authRecipientsServiceKeyInterceptor();
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertEquals(
                "test-recipients-key",
                template.headers()
                        .get("X-Auth-Recipients-Service-Key")
                        .iterator()
                        .next()
        );
        assertThat(template.headers().get(CorrelationIds.HEADER_NAME))
                .hasSize(1);
    }

    @Test
    void blankConfiguredKeyDoesNotCreateHeader() {
        AuthRecipientsFeignConfig config = new AuthRecipientsFeignConfig(
                "   ",
                123,
                456
        );
        RequestTemplate template = new RequestTemplate();

        config.authRecipientsServiceKeyInterceptor().apply(template);

        assertFalse(template.headers().containsKey("X-Auth-Recipients-Service-Key"));
    }

    @Test
    void readOnlyRecipientLookupHasExplicitTimeoutsAndBoundedRetry() {
        AuthRecipientsFeignConfig config = new AuthRecipientsFeignConfig(
                "key",
                123,
                456
        );

        Request.Options options = config.authRecipientsRequestOptions();
        Retryer retryer = config.authRecipientsRetryer();

        assertEquals(123, options.connectTimeoutMillis());
        assertEquals(456, options.readTimeoutMillis());
        assertThat(retryer)
                .isInstanceOf(Retryer.Default.class)
                .extracting("maxAttempts")
                .isEqualTo(2);
    }

    @Test
    void validCorrelationIdIsForwardedAlongsideServiceKey() {
        AuthRecipientsFeignConfig config = new AuthRecipientsFeignConfig(
                "key",
                123,
                456
        );
        RequestTemplate template = new RequestTemplate();
        MDC.put(CorrelationIds.MDC_KEY, "notification-event-9");

        config.authRecipientsServiceKeyInterceptor().apply(template);

        assertThat(template.headers().get(CorrelationIds.HEADER_NAME))
                .containsExactly("notification-event-9");
    }
}
