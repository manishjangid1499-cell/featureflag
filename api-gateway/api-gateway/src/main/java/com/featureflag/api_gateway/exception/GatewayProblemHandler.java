package com.featureflag.api_gateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.api_gateway.observability.CorrelationIds;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

/** Formats errors raised by the gateway; downstream HTTP responses pass through. */
@Component
@Order(-2)
public class GatewayProblemHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GatewayProblemHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(exception);
        }
        HttpStatusCode status = exception instanceof ErrorResponse error
                ? error.getStatusCode()
                : exception instanceof TimeoutException ? HttpStatus.GATEWAY_TIMEOUT
                : exception instanceof IOException ? HttpStatus.BAD_GATEWAY
                : HttpStatus.INTERNAL_SERVER_ERROR;
        HttpStatus knownStatus = HttpStatus.resolve(status.value());
        String code = status.is5xxServerError() ? "gateway-error" : "invalid-request";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                status.is5xxServerError() ? "The gateway could not complete the request"
                        : "The requested resource could not be served");
        problem.setTitle(knownStatus == null ? "Request Failed" : knownStatus.getReasonPhrase());
        problem.setType(URI.create("urn:feature-flag-platform:problem:" + code));
        problem.setInstance(URI.create(exchange.getRequest().getURI().getRawPath()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now().toString());

        String correlationId = exchange.getResponse().getHeaders().getFirst(CorrelationIds.HEADER_NAME);
        if (!CorrelationIds.isValid(correlationId)) {
            var incoming = exchange.getRequest().getHeaders().getOrEmpty(CorrelationIds.HEADER_NAME);
            correlationId = CorrelationIds.resolve(incoming.size() == 1 ? incoming.getFirst() : null);
        }
        problem.setProperty("correlationId", correlationId);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        exchange.getResponse().getHeaders().set(CorrelationIds.HEADER_NAME, correlationId);
        return Mono.fromCallable(() -> objectMapper.writeValueAsBytes(problem))
                .flatMap(bytes -> exchange.getResponse().writeWith(Mono.just(
                        exchange.getResponse().bufferFactory().wrap(bytes))));
    }
}
