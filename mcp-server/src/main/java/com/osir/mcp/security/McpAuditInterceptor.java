package com.osir.mcp.security;

import com.osir.mcp.services.McpAuthHelper;
import com.osir.mcp.telemetry.TelemetryEvents;
import com.osir.mcp.telemetry.TelemetryService;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jboss.logging.Logger;

@McpAudited
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class McpAuditInterceptor {

    private static final Logger AUDIT = Logger.getLogger("com.osir.mcp.audit");

    // The quarkiverse MCP library uses this sentinel as the default for Tool.name()
    private static final String TOOL_NAME_SENTINEL = "<<element name>>";

    @Inject
    TelemetryService telemetry;

    @Inject
    McpAuthHelper mcpAuthHelper;

    @AroundInvoke
    public Object audit(InvocationContext ctx) throws Exception {
        Tool toolAnnotation = ctx.getMethod().getAnnotation(Tool.class);
        if (toolAnnotation == null) {
            return ctx.proceed();
        }
        String annotationName = toolAnnotation.name();
        String toolName = (annotationName == null || annotationName.isBlank() || annotationName.equals(TOOL_NAME_SENTINEL))
                ? ctx.getMethod().getName()
                : annotationName;

        McpConnection connection = null;
        for (Object param : ctx.getParameters()) {
            if (param instanceof McpConnection conn) {
                connection = conn;
                break;
            }
        }
        String connId = connection != null ? connection.id() : "unknown";

        AUDIT.infof("tool=%s conn=%s", toolName, connId);

        long start = System.nanoTime();
        try {
            Object result = ctx.proceed();
            emit(connection, toolName, start, true, result);
            return result;
        } catch (Exception e) {
            emit(connection, toolName, start, false, null);
            throw e;
        }
    }

    /** Best-effort funnel event (A.7) — must never break the tool call it observes. */
    private void emit(McpConnection connection, String toolName, long startNanos, boolean success, Object result) {
        if (connection == null || !telemetry.enabled()) return;
        try {
            String principal = mcpAuthHelper.currentPrincipal(connection);
            String customerId = principal.startsWith("user:") ? principal.substring(5) : null;
            // Auth mode is resolved AFTER proceed(): tools publish their token to the
            // request-scoped AuthContext via setupAuth during the call.
            String authMode = mcpAuthHelper.bearerToken() != null ? "oauth"
                    : customerId != null ? "device"
                    : "anonymous";
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            telemetry.record(TelemetryEvents.event(
                    connection, toolName, authMode, customerId, durationMs, success, result));
        } catch (Exception e) {
            AUDIT.debugf("Telemetry emit failed for tool=%s: %s", toolName, e.getMessage());
        }
    }
}
