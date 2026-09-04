package com.featureflag.flag_service.config;

import com.featureflag.flag_service.observability.CorrelationIds;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

public class AuthRecipientsFeignConfig {

    private static final String SERVICE_KEY_HEADER =
            "X-Auth-Recipients-Service-Key";

    private final String serviceKey;
    private final long connectTimeoutMillis;
    private final long readTimeoutMillis;

    public AuthRecipientsFeignConfig(
            @Value("${AUTH_RECIPIENTS_SERVICE_KEY:}")
            String serviceKey,
            @Value("${clients.auth-recipients.connect-timeout-ms:2000}")
            long connectTimeoutMillis,
            @Value("${clients.auth-recipients.read-timeout-ms:3000}")
            long readTimeoutMillis
    ) {
        this.serviceKey = serviceKey;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    @Bean
    public RequestInterceptor authRecipientsHeaders() {
        return template -> {
            if (StringUtils.hasText(serviceKey)) {
                template.header(SERVICE_KEY_HEADER, serviceKey);
            }
            template.header(
                    CorrelationIds.HEADER_NAME,
                    CorrelationIds.currentOrGenerate()
            );
        };
    }

    @Bean
    public Request.Options authRecipientsRequestOptions() {
        return new Request.Options(
                connectTimeoutMillis,
                TimeUnit.MILLISECONDS,
                readTimeoutMillis,
                TimeUnit.MILLISECONDS,
                true
        );
    }

    @Bean
    public Retryer authRecipientsRetryer() {
        // Recipient resolution is an idempotent GET. At most one retry.
        return new Retryer.Default(100, 200, 2);
    }
}
