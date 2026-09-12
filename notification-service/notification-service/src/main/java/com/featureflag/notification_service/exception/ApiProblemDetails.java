package com.featureflag.notification_service.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.notification_service.observability.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

public class ApiProblemDetails {

    private static final String TYPE_PREFIX =
            "urn:feature-flag-platform:problem:";

    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public ApiProblemDetails(ObjectMapper objectMapper) {
        this(objectMapper, Clock.systemUTC());
    }

    public ApiProblemDetails(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ProblemDetail create(
            HttpStatus status,
            String code,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + code));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", clock.instant().toString());

        String correlationId = MDC.get(CorrelationIds.MDC_KEY);
        if (StringUtils.hasText(correlationId)) {
            problem.setProperty("correlationId", correlationId);
        }
        return problem;
    }

    public ResponseEntity<ProblemDetail> response(
            HttpStatus status,
            String code,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(create(status, code, title, detail, request));
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String title,
            String detail
    ) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                create(status, code, title, detail, request)
        );
    }
}
