package com.featureflag.flag_service.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        List<String> incoming = Collections.list(
                request.getHeaders(CorrelationIds.HEADER_NAME)
        );
        String candidate = incoming.size() == 1
                ? incoming.getFirst()
                : null;
        String correlationId = CorrelationIds.resolve(candidate);

        response.setHeader(
                CorrelationIds.HEADER_NAME,
                correlationId
        );

        try (MDC.MDCCloseable ignored = MDC.putCloseable(
                CorrelationIds.MDC_KEY,
                correlationId
        )) {
            filterChain.doFilter(request, response);
        }
    }
}
