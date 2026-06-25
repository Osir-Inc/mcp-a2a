package com.osir.mcp.clients;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.MDC;

/**
 * REST client filter that propagates X-Request-ID from MDC to outgoing backend calls.
 * Enables end-to-end request tracing across A2A → backend.
 */
@Provider
public class RequestIdPropagationFilter implements ClientRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String MDC_KEY = "requestId";

    @Override
    public void filter(ClientRequestContext ctx) {
        Object requestId = MDC.get(MDC_KEY);
        if (requestId != null) {
            ctx.getHeaders().putSingle(REQUEST_ID_HEADER, requestId.toString());
        }
    }
}
