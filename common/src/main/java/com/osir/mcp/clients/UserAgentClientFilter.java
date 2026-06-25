package com.osir.mcp.clients;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import org.eclipse.microprofile.config.ConfigProvider;

public class UserAgentClientFilter implements ClientRequestFilter {

    private static final String USER_AGENT = ConfigProvider.getConfig()
            .getOptionalValue("osir.client.user-agent", String.class)
            .orElse("OSIR-Server/2.0");

    private static final String CLIENT_SOURCE = ConfigProvider.getConfig()
            .getOptionalValue("osir.client.source", String.class)
            .orElse("osir-mcp");

    @Override
    public void filter(ClientRequestContext ctx) {
        ctx.getHeaders().putSingle("User-Agent", USER_AGENT);
        ctx.getHeaders().putSingle("X-Client-Source", CLIENT_SOURCE);
    }
}
