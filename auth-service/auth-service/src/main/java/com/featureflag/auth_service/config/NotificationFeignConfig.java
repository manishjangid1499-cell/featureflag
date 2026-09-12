package com.featureflag.auth_service.config;

import com.featureflag.auth_service.observability.CorrelationIds;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

public class NotificationFeignConfig {

    private final long connectTimeoutMillis;
    private final long readTimeoutMillis;

    public NotificationFeignConfig(
            @Value("${clients.notification.connect-timeout-ms:2000}")
            long connectTimeoutMillis,
            @Value("${clients.notification.read-timeout-ms:35000}")
            long readTimeoutMillis
    ) {
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    @Bean
    public Request.Options notificationRequestOptions() {
        return new Request.Options(
                connectTimeoutMillis,
                TimeUnit.MILLISECONDS,
                readTimeoutMillis,
                TimeUnit.MILLISECONDS,
                true
        );
    }

    @Bean
    public Retryer notificationRetryer() {
        // Invitation delivery is a non-idempotent POST. A timeout must not
        // cause an automatic duplicate email send.
        return Retryer.NEVER_RETRY;
    }

    @Bean
    public RequestInterceptor notificationCorrelationInterceptor() {
        return template -> template.header(
                CorrelationIds.HEADER_NAME,
                CorrelationIds.currentOrGenerate()
        );
    }
}
