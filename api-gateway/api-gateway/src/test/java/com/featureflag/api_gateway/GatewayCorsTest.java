package com.featureflag.api_gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(properties = {
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false"
})
@AutoConfigureWebTestClient
@ActiveProfiles("docker")
class GatewayCorsTest {
    @Autowired private WebTestClient client;

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:3000", "http://127.0.0.1:3000",
            "http://localhost:5173", "http://127.0.0.1:5173"})
    void actualDockerCorsPolicyAllowsSupportedBrowserOrigins(String origin) {
        client.options().uri("http://gateway.test/auth/login").header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,authorization")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", origin);
    }

    @Test
    void unrelatedOriginIsForbiddenEvenForLogin() {
        client.post().uri("http://gateway.test/auth/login").header("Origin", "https://untrusted.example")
                .exchange().expectStatus().isForbidden()
                .expectHeader().doesNotExist("Access-Control-Allow-Origin");
    }

    @Test
    void composePostPassesCorsAndReachesRouting() {
        // No discovery instance in this test: 503 proves CORS allowed the actual POST.
        client.post().uri("http://gateway.test/auth/login").header("Origin", "http://localhost:3000")
                .exchange().expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:3000");
    }
}
