package com.featureflag.sdk;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

public final class FeatureFlagClient {

    public static final String SDK_KEY_HEADER =
            "X-Feature-Flag-Key";
    private static final String SDK_KEY_PREFIX = "ff_sdk_";
    private static final int SDK_KEY_SECRET_LENGTH = 43;

    private final String baseUrl;
    private final String sdkKey;
    private final Duration requestTimeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private FeatureFlagClient(Builder builder) {
        this.baseUrl = normalizeBaseUrl(builder.baseUrl);
        this.sdkKey = requireSdkKey(builder.sdkKey);
        this.requestTimeout = positive(
                builder.requestTimeout,
                "requestTimeout"
        );
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(positive(
                        builder.connectTimeout,
                        "connectTimeout"
                ))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        false
                );
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEnabled(
            String flagKey,
            String subject,
            boolean defaultValue
    ) {
        String requiredFlagKey = requireValue(flagKey, "flagKey");
        String requiredSubject = requireValue(subject, "subject");
        URI uri = URI.create(
                baseUrl
                        + "/runtime/v1/flags/"
                        + encode(requiredFlagKey)
                        + "/evaluate?subject="
                        + encode(requiredSubject)
        );
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header(SDK_KEY_HEADER, sdkKey)
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );
            if (response.statusCode() != 200) {
                return defaultValue;
            }

            RuntimeEvaluation evaluation = objectMapper.readValue(
                    response.body(),
                    RuntimeEvaluation.class
            );
            return evaluation.enabled() == null
                    ? defaultValue
                    : evaluation.enabled();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return defaultValue;
        } catch (IOException | RuntimeException exception) {
            return defaultValue;
        }
    }

    private static String normalizeBaseUrl(String value) {
        String baseUrl = requireValue(value, "baseUrl");
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "baseUrl must be a valid HTTP(S) URL",
                    exception
            );
        }
        if (!("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "baseUrl must be a valid HTTP(S) URL without query or fragment"
            );
        }

        String normalized = baseUrl;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(
                    0,
                    normalized.length() - 1
            );
        }
        return normalized;
    }

    private static String requireSdkKey(String value) {
        String key = requireValue(value, "sdkKey");
        if (!key.startsWith(SDK_KEY_PREFIX)
                || key.length()
                != SDK_KEY_PREFIX.length() + SDK_KEY_SECRET_LENGTH) {
            throw invalidSdkKey();
        }

        for (int index = SDK_KEY_PREFIX.length();
             index < key.length();
             index++) {
            if (!isBase64UrlCharacter(key.charAt(index))) {
                throw invalidSdkKey();
            }
        }
        return key;
    }

    private static boolean isBase64UrlCharacter(char value) {
        return value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '-'
                || value == '_';
    }

    private static IllegalArgumentException invalidSdkKey() {
        return new IllegalArgumentException(
                "sdkKey has an invalid format"
        );
    }

    private static String requireValue(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }
        return value.trim();
    }

    private static Duration positive(
            Duration duration,
            String field
    ) {
        Objects.requireNonNull(duration, field + " must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(
                    field + " must be positive"
            );
        }
        return duration;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private record RuntimeEvaluation(
            String flagKey,
            String environment,
            Boolean enabled
    ) {
    }

    public static final class Builder {

        private String baseUrl;
        private String sdkKey;
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration requestTimeout = Duration.ofSeconds(3);

        private Builder() {
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder sdkKey(String sdkKey) {
            this.sdkKey = sdkKey;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public FeatureFlagClient build() {
            return new FeatureFlagClient(this);
        }
    }
}
