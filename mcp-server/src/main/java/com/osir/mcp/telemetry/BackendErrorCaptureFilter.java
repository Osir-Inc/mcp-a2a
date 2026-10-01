package com.osir.mcp.telemetry;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;

/**
 * Reports every backend 5xx, on every REST client, registered globally next to
 * {@code UserAgentClientFilter}.
 *
 * <p>This is where the coverage comes from. The tool layer has no single error chokepoint: four
 * places call {@code ToolErrors.toolError}, a hundred-odd others catch the exception, log it and
 * return {@code success:false}. A 5xx the tool swallows that way is invisible to the user, who just
 * sees the agent give up, and invisible to us too. A response filter sees all of them, before the
 * client turns the response into an exception or the tool decides to hide it.
 *
 * <p>It deliberately cannot say which tool was running, only which endpoint failed. For a backend
 * outage that is the more useful key anyway, and it costs nothing per tool.
 */
public class BackendErrorCaptureFilter implements ClientResponseFilter {

    @Override
    public void filter(ClientRequestContext request, ClientResponseContext response) {
        // 4xx is the user or the business (DOMAIN_NOT_AVAILABLE, INSUFFICIENT_FUNDS, an expired
        // session). Only a 5xx is ours to fix.
        if (response.getStatus() < 500) return;
        ErrorReporting.captureBackendFailure(
                request.getMethod(), request.getUri().getPath(), response.getStatus());
    }
}
