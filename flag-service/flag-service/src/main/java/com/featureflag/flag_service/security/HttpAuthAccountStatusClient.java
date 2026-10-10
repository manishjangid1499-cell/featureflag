package com.featureflag.flag_service.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

public class HttpAuthAccountStatusClient implements AuthAccountStatusClient {

    private static final Logger log = LoggerFactory.getLogger(HttpAuthAccountStatusClient.class);
    private static final String SERVICE_KEY_HEADER = "X-Internal-Service-Key";

    private final RestTemplate restTemplate;
    private final String authServiceUrl;
    private final String serviceKey;

    public HttpAuthAccountStatusClient(
            String authServiceUrl,
            String serviceKey,
            long connectTimeoutMs,
            long readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connectTimeoutMs);
        factory.setReadTimeout((int) readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
        this.authServiceUrl = authServiceUrl.endsWith("/")
                ? authServiceUrl.substring(0, authServiceUrl.length() - 1)
                : authServiceUrl;
        this.serviceKey = serviceKey;
    }

    @Override
    public Optional<UserAccountStatus> getAccountStatus(String email, Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId is required and must be positive");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            if (serviceKey != null && !serviceKey.isBlank()) {
                headers.set(SERVICE_KEY_HEADER, serviceKey);
            }
            HttpEntity<?> entity = new HttpEntity<>(headers);
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(authServiceUrl + "/internal/users/status")
                    .queryParam("email", email)
                    .queryParam("userId", userId)
                    .build()
                    .toUri();

            ResponseEntity<UserAccountStatus> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    entity,
                    UserAccountStatus.class
            );

            return Optional.ofNullable(response.getBody());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            log.error("Internal service authentication failed when contacting auth-service");
            throw new AuthServiceUnavailableException("Internal authentication failure communicating with auth-service", e);
        } catch (ResourceAccessException e) {
            log.error("Auth-service is unreachable or timed out: {}", e.getMessage());
            throw new AuthServiceUnavailableException("Auth-service is unreachable or timed out", e);
        } catch (HttpStatusCodeException e) {
            log.error("Auth-service returned error status: {}", e.getStatusCode());
            throw new AuthServiceUnavailableException("Auth-service returned error status: " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Unexpected error contacting auth-service: {}", e.getMessage());
            throw new AuthServiceUnavailableException("Failed to verify account status with auth-service", e);
        }
    }
}
