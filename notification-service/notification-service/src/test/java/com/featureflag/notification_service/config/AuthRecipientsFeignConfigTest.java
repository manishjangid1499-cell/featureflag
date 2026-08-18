package com.featureflag.notification_service.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AuthRecipientsFeignConfigTest {

    @Test
    void recipientClientInterceptorAddsInternalHeader() {
        AuthRecipientsFeignConfig config =
                new AuthRecipientsFeignConfig("test-recipients-key");
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
    }

    @Test
    void blankConfiguredKeyDoesNotCreateHeader() {
        AuthRecipientsFeignConfig config = new AuthRecipientsFeignConfig("   ");
        RequestTemplate template = new RequestTemplate();

        config.authRecipientsServiceKeyInterceptor().apply(template);

        assertFalse(template.headers().containsKey("X-Auth-Recipients-Service-Key"));
    }
}
