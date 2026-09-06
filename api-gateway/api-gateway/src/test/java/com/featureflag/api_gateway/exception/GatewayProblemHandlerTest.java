package com.featureflag.api_gateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayProblemHandlerTest {
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
    private final GatewayProblemHandler handler = new GatewayProblemHandler(mapper);

    @Test
    void frameworkStatusesArePreservedWithoutInternalDetails() throws Exception {
        for (HttpStatus status : new HttpStatus[]{HttpStatus.NOT_FOUND, HttpStatus.SERVICE_UNAVAILABLE}) {
            var exchange = exchange();
            handler.handle(exchange, new ResponseStatusException(status, "password=PRIVATE_VALUE")).block();
            var body = mapper.readTree(exchange.getResponse().getBodyAsString().block());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(status);
            assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(body.path("status").asInt()).isEqualTo(status.value());
            assertThat(body.toString()).doesNotContain("PRIVATE_VALUE", "ResponseStatusException");
        }
    }

    @Test
    void unavailableDownstreamAndTimeoutMapTo502And504() {
        var connection = exchange();
        handler.handle(connection, new ConnectException("PRIVATE_VALUE")).block();
        assertThat(connection.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        var timeout = exchange();
        handler.handle(timeout, new TimeoutException("PRIVATE_VALUE")).block();
        assertThat(timeout.getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void unexpectedFailureIsSanitizedAndUsesExistingCorrelation() throws Exception {
        var exchange = exchange();
        exchange.getResponse().getHeaders().set("X-Correlation-ID", "existing-correlation");
        handler.handle(exchange, new IllegalStateException("PRIVATE_VALUE")).block();
        var body = mapper.readTree(exchange.getResponse().getBodyAsString().block());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body.path("correlationId").asText()).isEqualTo("existing-correlation");
        assertThat(body.path("instance").asText()).isEqualTo("/flags");
        assertThat(body.toString()).doesNotContain("PRIVATE_VALUE", "IllegalStateException");
    }

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/flags?token=PRIVATE_VALUE")
                .header("X-Correlation-ID", "incoming-correlation"));
    }
}
