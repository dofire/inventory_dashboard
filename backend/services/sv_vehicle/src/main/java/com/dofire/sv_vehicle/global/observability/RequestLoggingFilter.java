package com.dofire.sv_vehicle.global.observability;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** Runs inside the server observation filter, while trace MDC is still available. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private final Tracer tracer;

    public RequestLoggingFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader("X-Request-ID"));
        String traceId = tracer.currentSpan() != null
                ? tracer.currentSpan().context().traceId()
                : UUID.randomUUID().toString().replace("-", "");
        response.setHeader("X-Request-ID", requestId);
        response.setHeader("X-Trace-ID", traceId);
        String previousRequestId = MDC.get("requestId");
        MDC.put("requestId", requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Avoid logging query parameters, bodies, or credentials.
            try {
                log.info("HTTP {} {} status={}", request.getMethod(), request.getRequestURI(), response.getStatus());
            } finally {
                if (previousRequestId == null) {
                    MDC.remove("requestId");
                } else {
                    MDC.put("requestId", previousRequestId);
                }
            }
        }
    }

    private String resolveRequestId(String value) {
        if (value != null && value.length() == 36) {
            try {
                String canonical = UUID.fromString(value).toString();
                if (canonical.equalsIgnoreCase(value)) {
                    return canonical;
                }
            } catch (IllegalArgumentException ignored) {
                // Replace malformed IDs rather than logging untrusted header text.
            }
        }
        return UUID.randomUUID().toString();
    }
}
