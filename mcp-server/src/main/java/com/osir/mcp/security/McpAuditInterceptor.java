package com.osir.mcp.security;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import jakarta.annotation.Priority;
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

        String connId = "unknown";
        for (Object param : ctx.getParameters()) {
            if (param instanceof McpConnection conn) {
                connId = conn.id();
                break;
            }
        }

        AUDIT.infof("tool=%s conn=%s", toolName, connId);
        return ctx.proceed();
    }
}
