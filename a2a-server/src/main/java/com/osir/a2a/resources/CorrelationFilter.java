package com.osir.a2a.resources;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.MDC;

import java.util.UUID;

/**
 * Sets a correlation ID (X-Request-ID) on every request via MDC.
 * The ID is propagated to all log entries for tracing.
 * MDC is always cleaned up in the response filter via try-finally.
 */
@Provider
public class CorrelationFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String MDC_KEY = "requestId";

    @Override
    public void filter(ContainerRequestContext request) {
        String requestId = request.getHeaderString(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }
        MDC.put(MDC_KEY, requestId);
        request.setProperty(MDC_KEY, requestId);
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        try {
            String requestId = (String) request.getProperty(MDC_KEY);
            if (requestId != null) {
                response.getHeaders().putSingle(REQUEST_ID_HEADER, requestId);
            }
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
