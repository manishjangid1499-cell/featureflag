package com.featureflag.notification_service.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

public class AuthRecipientsFeignConfig {

    private static final String HEADER_NAME = "X-Auth-Recipients-Service-Key";

    private final String serviceKey;

    public AuthRecipientsFeignConfig(
            @Value("${AUTH_RECIPIENTS_SERVICE_KEY:}") String serviceKey
    ) {
        this.serviceKey = serviceKey;
    }

    @Bean
    public RequestInterceptor authRecipientsServiceKeyInterceptor() {
        return requestTemplate -> {
            if (StringUtils.hasText(serviceKey)) {
                requestTemplate.header(HEADER_NAME, serviceKey);
            }
        };
    }
}
