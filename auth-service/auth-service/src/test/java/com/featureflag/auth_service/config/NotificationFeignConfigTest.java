package com.featureflag.auth_service.config;

import com.featureflag.auth_service.observability.CorrelationIds;
import feign.Request;
import feign.RequestTemplate;
import feign.Retryer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationFeignConfigTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void nonIdempotentInvitationClientHasExplicitTimeoutsAndNoRetry() {
        NotificationFeignConfig config = new NotificationFeignConfig(123, 456);

        Request.Options options = config.notificationRequestOptions();

        assertThat(options.connectTimeoutMillis()).isEqualTo(123);
        assertThat(options.readTimeoutMillis()).isEqualTo(456);
        assertThat(config.notificationRetryer())
                .isSameAs(Retryer.NEVER_RETRY);
    }

    @Test
    void correlationIdIsPropagated() {
        NotificationFeignConfig config = new NotificationFeignConfig(123, 456);
        RequestTemplate template = new RequestTemplate();
        MDC.put(CorrelationIds.MDC_KEY, "auth-request-42");

        config.notificationCorrelationInterceptor().apply(template);

        assertThat(template.headers().get(CorrelationIds.HEADER_NAME))
                .containsExactly("auth-request-42");
    }
}
