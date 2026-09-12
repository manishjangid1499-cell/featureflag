package com.featureflag.api_gateway.observability;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdGlobalFilterTest {

    private final CorrelationIdGlobalFilter filter =
            new CorrelationIdGlobalFilter();

    @Test
    void validIncomingValueIsForwardedReturnedAndPlacedInContext() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/flags")
                        .header(CorrelationIds.HEADER_NAME, "gateway-42")
        );
        AtomicReference<String> requestHeader = new AtomicReference<>();
        AtomicReference<String> contextValue = new AtomicReference<>();
        GatewayFilterChain chain = correlated -> {
            requestHeader.set(correlated.getRequest().getHeaders()
                    .getFirst(CorrelationIds.HEADER_NAME));
            return Mono.deferContextual(context -> {
                contextValue.set(context.get(CorrelationIds.CONTEXT_KEY));
                return Mono.empty();
            });
        };

        filter.filter(exchange, chain).block();

        assertThat(requestHeader).hasValue("gateway-42");
        assertThat(contextValue).hasValue("gateway-42");
        assertThat(exchange.getResponse().getHeaders()
                .getFirst(CorrelationIds.HEADER_NAME))
                .isEqualTo("gateway-42");
    }

    @Test
    void missingAndOversizedValuesAreReplaced() {
        assertReplaced(MockServerHttpRequest.get("/flags"));
        assertReplaced(MockServerHttpRequest.get("/flags")
                .header(CorrelationIds.HEADER_NAME, "x".repeat(65)));
    }

    private void assertReplaced(MockServerHttpRequest.BaseBuilder<?> request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<String> forwarded = new AtomicReference<>();

        filter.filter(exchange, correlated -> {
            forwarded.set(correlated.getRequest().getHeaders()
                    .getFirst(CorrelationIds.HEADER_NAME));
            return Mono.empty();
        }).block();

        assertThat(forwarded.get())
                .hasSize(36)
                .matches("[A-Za-z0-9._-]+");
    }
}
