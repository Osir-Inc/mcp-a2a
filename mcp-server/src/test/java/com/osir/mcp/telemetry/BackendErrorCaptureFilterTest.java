package com.osir.mcp.telemetry;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The filter is the only thing that sees a 5xx a tool swallowed, so the status gate is worth a test:
 * too wide and the shared event quota pays for every expired session, too narrow and we are blind.
 */
class BackendErrorCaptureFilterTest {

    private static ClientRequestContext request(String method, String path) {
        ClientRequestContext request = mock(ClientRequestContext.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getUri()).thenReturn(URI.create("https://be.osir.com" + path));
        return request;
    }

    private static ClientResponseContext response(int status) {
        ClientResponseContext response = mock(ClientResponseContext.class);
        when(response.getStatus()).thenReturn(status);
        return response;
    }

    @Test
    void readsTheRouteOnlyForA5xx() {
        BackendErrorCaptureFilter filter = new BackendErrorCaptureFilter();

        ClientRequestContext ok = request("GET", "/v1/domains/example.com");
        filter.filter(ok, response(200));
        filter.filter(ok, response(404));
        filter.filter(ok, response(409));
        verify(ok, never()).getUri();

        ClientRequestContext broken = request("POST", "/v1/hosting/vps/order");
        filter.filter(broken, response(503));
        verify(broken).getUri();
    }
}
