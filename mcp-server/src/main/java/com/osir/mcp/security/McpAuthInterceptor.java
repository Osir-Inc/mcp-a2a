package com.osir.mcp.security;

import com.osir.mcp.services.McpAuthHelper;
import com.osir.mcp.services.SessionAwareAuthService;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jboss.logging.Logger;

@RequiresAuth
@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 100)
public class McpAuthInterceptor {

    private static final Logger LOG = Logger.getLogger(McpAuthInterceptor.class);

    private static final String AUTH_REQUIRED_MSG =
            "Authentication required. Call loginWithDevice and complete the verification flow before retrying this tool.";
    private static final String AUTH_EXPIRED_MSG =
            "Session expired. Call loginWithDevice and complete the verification flow to re-authenticate, then retry this tool.";

    @Inject
    SessionAwareAuthService sessionService;

    @Inject
    McpAuthHelper mcpAuthHelper;

    @AroundInvoke
    public Object checkAuth(InvocationContext ctx) throws Exception {
        if (ctx.getMethod().getAnnotation(Tool.class) == null) {
            return ctx.proceed();
        }

        McpConnection conn = null;
        for (Object param : ctx.getParameters()) {
            if (param instanceof McpConnection c) {
                conn = c;
                break;
            }
        }
        if (conn == null) return ctx.proceed();

        return switch (sessionService.checkAuth(conn.id())) {
            case NOT_AUTHENTICATED -> {
                LOG.debugf("tool=%s conn=%s: no session", ctx.getMethod().getName(), conn.id());
                throw new ToolCallException(AUTH_REQUIRED_MSG);
            }
            case EXPIRED -> {
                LOG.debugf("tool=%s conn=%s: session expired", ctx.getMethod().getName(), conn.id());
                throw new ToolCallException(AUTH_EXPIRED_MSG);
            }
            case AUTHENTICATED -> {
                mcpAuthHelper.setupAuth(conn);
                yield ctx.proceed();
            }
        };
    }
}
