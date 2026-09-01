package com.featureflag.sdk;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeatureFlagClientTest {

    private static final String SDK_KEY =
            "ff_sdk_" + "A".repeat(43);

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> response =
            new AtomicReference<>("{\"enabled\":true}");
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicLong responseDelayMillis = new AtomicLong();
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> receivedKey =
            new AtomicReference<>();
    private final AtomicReference<String> receivedUri =
            new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );
        server.createContext(
                "/runtime/v1/flags",
                this::handle
        );
        server.start();
        baseUrl = "http://127.0.0.1:"
                + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void trueAndFalseResponsesAreReturned() {
        FeatureFlagClient client = client();

        assertThat(client.isEnabled(
                "checkout",
                "user-1",
                false
        )).isTrue();

        response.set("{\"enabled\":false}");
        assertThat(client.isEnabled(
                "checkout",
                "user-1",
                true
        )).isFalse();
    }

    @Test
    void keyUsesHeaderAndNeverAppearsInUrl() {
        FeatureFlagClient client = client();

        client.isEnabled("new checkout", "user/one + two", false);

        assertThat(receivedKey.get()).isEqualTo(SDK_KEY);
        assertThat(receivedUri.get())
                .contains("new%20checkout")
                .contains("subject=user%2Fone%20%2B%20two")
                .doesNotContain(SDK_KEY);
    }

    @Test
    void networkFailureReturnsCallerDefault() {
        FeatureFlagClient client = client();
        server.stop(0);
        server = null;

        assertThat(client.isEnabled(
                "checkout",
                "user-1",
                true
        )).isTrue();
    }

    @Test
    void requestTimeoutReturnsCallerDefault() {
        responseDelayMillis.set(250);
        FeatureFlagClient client = FeatureFlagClient.builder()
                .baseUrl(baseUrl)
                .sdkKey(SDK_KEY)
                .requestTimeout(Duration.ofMillis(25))
                .build();

        assertThat(client.isEnabled(
                "checkout",
                "user-1",
                true
        )).isTrue();
    }

    @Test
    void unauthorizedReturnsCallerDefault() {
        status.set(401);
        assertThat(client().isEnabled(
                "checkout",
                "user-1",
                true
        )).isTrue();
    }

    @Test
    void serverFailureReturnsCallerDefault() {
        status.set(500);
        assertThat(client().isEnabled(
                "checkout",
                "user-1",
                false
        )).isFalse();
    }

    @Test
    void malformedOrIncompleteResponseReturnsCallerDefault() {
        response.set("not-json");
        assertThat(client().isEnabled(
                "checkout",
                "user-1",
                true
        )).isTrue();

        response.set("{\"flagKey\":\"checkout\"}");
        assertThat(client().isEnabled(
                "checkout",
                "user-1",
                false
        )).isFalse();
    }

    @Test
    void oneClientIsReusableAcrossCalls() {
        FeatureFlagClient client = client();

        client.isEnabled("one", "subject", false);
        client.isEnabled("two", "subject", false);
        client.isEnabled("three", "subject", false);

        assertThat(requests).hasValue(3);
    }

    @Test
    void invalidConfigurationFailsWithoutEchoingCredential() {
        for (String invalidKey : List.of(
                "not-a-key",
                "other_" + "A".repeat(44),
                "ff_sdk_" + "A".repeat(42),
                "ff_sdk_" + "A".repeat(44),
                "ff_sdk_" + "A".repeat(42) + "!",
                "ff_sdk_" + "A".repeat(42) + "é"
        )) {
            assertThatThrownBy(
                    () -> FeatureFlagClient.builder()
                            .baseUrl(baseUrl)
                            .sdkKey(invalidKey)
                            .build()
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("sdkKey has an invalid format")
                    .hasMessageNotContaining(invalidKey);
        }
    }

    @Test
    void urlSafeSdkKeyCharactersAreAccepted() {
        String urlSafeKey = "ff_sdk_"
                + "A".repeat(41)
                + "-_";

        FeatureFlagClient client = FeatureFlagClient.builder()
                .baseUrl(baseUrl)
                .sdkKey(urlSafeKey)
                .build();

        assertThat(client).isNotNull();
    }

    private FeatureFlagClient client() {
        return FeatureFlagClient.builder()
                .baseUrl(baseUrl)
                .sdkKey(SDK_KEY)
                .build();
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        receivedKey.set(exchange.getRequestHeaders().getFirst(
                FeatureFlagClient.SDK_KEY_HEADER
        ));
        receivedUri.set(exchange.getRequestURI().toASCIIString());
        delayResponse();
        byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add(
                "Content-Type",
                "application/json"
        );
        exchange.sendResponseHeaders(status.get(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void delayResponse() {
        try {
            Thread.sleep(responseDelayMillis.get());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
