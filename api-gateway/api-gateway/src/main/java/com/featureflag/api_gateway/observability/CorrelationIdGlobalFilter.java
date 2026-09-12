package com.featureflag.api_gateway.observability;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class CorrelationIdGlobalFilter
        implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain
    ) {
        List<String> incoming = exchange.getRequest()
                .getHeaders()
                .getOrEmpty(CorrelationIds.HEADER_NAME);
        String candidate = incoming.size() == 1
                ? incoming.getFirst()
                : null;
        String correlationId = CorrelationIds.resolve(candidate);

        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.set(
                        CorrelationIds.HEADER_NAME,
                        correlationId
                ))
                .build();
        ServerWebExchange correlatedExchange = exchange.mutate()
                .request(request)
                .build();

        correlatedExchange.getResponse().getHeaders().set(
                CorrelationIds.HEADER_NAME,
                correlationId
        );

        return chain.filter(correlatedExchange)
                .contextWrite(context -> context.put(
                        CorrelationIds.CONTEXT_KEY,
                        correlationId
                ));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
