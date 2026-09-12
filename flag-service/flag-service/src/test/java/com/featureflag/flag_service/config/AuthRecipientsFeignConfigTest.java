package com.featureflag.flag_service.config;

import com.featureflag.flag_service.observability.CorrelationIds;
import feign.Request;
import feign.RequestTemplate;
import feign.Retryer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRecipientsFeignConfigTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void addsServiceKeyAndCurrentCorrelationId() {
        AuthRecipientsFeignConfig config =
                new AuthRecipientsFeignConfig("service-key", 123, 456);
        RequestTemplate template = new RequestTemplate();
        MDC.put(CorrelationIds.MDC_KEY, "operation-9");

        config.authRecipientsHeaders().apply(template);

        assertThat(template.headers()
                .get("X-Auth-Recipients-Service-Key"))
                .containsExactly("service-key");
        assertThat(template.headers().get(CorrelationIds.HEADER_NAME))
                .containsExactly("operation-9");
    }

    @Test
    void blankServiceKeyIsNotSent() {
        AuthRecipientsFeignConfig config =
                new AuthRecipientsFeignConfig("  ", 123, 456);
        RequestTemplate template = new RequestTemplate();

        config.authRecipientsHeaders().apply(template);

        assertThat(template.headers())
                .doesNotContainKey("X-Auth-Recipients-Service-Key");
    }

    @Test
    void idempotentLookupUsesExplicitTimeoutsAndOneRetry() {
        AuthRecipientsFeignConfig config =
                new AuthRecipientsFeignConfig("key", 123, 456);

        Request.Options options = config.authRecipientsRequestOptions();
        Retryer retryer = config.authRecipientsRetryer();

        assertThat(options.connectTimeoutMillis()).isEqualTo(123);
        assertThat(options.readTimeoutMillis()).isEqualTo(456);
        assertThat(retryer)
                .isInstanceOf(Retryer.Default.class)
                .extracting("maxAttempts")
                .isEqualTo(2);
    }
}
