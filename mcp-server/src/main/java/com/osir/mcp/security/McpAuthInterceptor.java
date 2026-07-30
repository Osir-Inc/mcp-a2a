package com.osir.mcp.security;

import com.osir.mcp.services.McpAuthHelper;
import com.osir.mcp.services.SessionAwareAuthService;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jboss.logging.Logger;

import java.lang.reflect.Parameter;

@RequiresAuth
@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 100)
public class McpAuthInterceptor {

    private static final Logger LOG = Logger.getLogger(McpAuthInterceptor.class);

    private static final String AUTH_REQUIRED_MSG =
            "Authentication required. Call loginWithDevice and complete the verification flow, then retry this tool "
                    + "passing the sessionKey returned by checkDeviceLoginStatus.";
    private static final String AUTH_EXPIRED_MSG =
            "Session expired. Call loginWithDevice and complete the verification flow to re-authenticate, then retry this tool.";
    private static final String SESSION_KEY_INVALID_MSG =
            "The provided sessionKey is expired, logged out, or unknown. Call loginWithDevice to start a new login, "
                    + "then retry with the new sessionKey from checkDeviceLoginStatus.";

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

        // Resolution precedence lives in McpAuthHelper: Bearer header (OAuth connectors) >
        // conversation sessionKey argument (in-chat device login, survives MCP session churn) >
        // per-connection session. The sessionKey is a declared-but-unused tool parameter; it is
        // extracted here so individual tools never handle auth themselves.
        String sessionKey = findSessionKey(ctx);
        if (mcpAuthHelper.setupAuth(conn, sessionKey)) {
            return ctx.proceed();
        }

        if (sessionKey != null && !sessionKey.isBlank()) {
            LOG.debugf("tool=%s conn=%s: sessionKey rejected", ctx.getMethod().getName(), conn.id());
            throw new ToolCallException(SESSION_KEY_INVALID_MSG);
        }

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

    /** The value of the tool's optional sessionKey parameter, or null when absent/undeclared. */
    private String findSessionKey(InvocationContext ctx) {
        Parameter[] params = ctx.getMethod().getParameters();
        for (int i = 0; i < params.length; i++) {
            ToolArg arg = params[i].getAnnotation(ToolArg.class);
            boolean named = (arg != null && RequiresAuth.SESSION_KEY.equals(arg.name()))
                    || RequiresAuth.SESSION_KEY.equals(params[i].getName());
            if (named && ctx.getParameters()[i] instanceof String s) {
                return s;
            }
        }
        return null;
    }
}
