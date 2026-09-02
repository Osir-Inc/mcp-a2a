package com.osir.mcp.telemetry;

import com.osir.mcp.models.auth.DeviceLoginStatusResult;
import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import io.quarkiverse.mcp.server.McpConnection;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Builds AgentEventDTO maps for the backend funnel. Pure functions, no I/O. */
public final class TelemetryEvents {

    private static final Set<String> QUOTE_TOOLS = Set.of(
            "checkDomainAvailability", "getDomainPricing", "getTransferQuote",
            "getMailboxQuote", "previewPaymentFees", "getHostingBundle");

    private static final Set<String> DEPLOY_TOOLS = Set.of("osirAppDeploy", "osirSitePublish");

    private TelemetryEvents() {
    }

    /**
     * The funnel stage this tool call represents, or null when it doesn't advance the funnel.
     * ponytail: "registered" is not observable here — it happens inside executeConfirmedAction's
     * staged callback; emit it from PendingActionStore if the funnel ever needs the distinction
     * beyond "confirmed".
     */
    public static String stage(String tool, boolean success, Object result) {
        if (!success) return null;
        if (result instanceof DeviceLoginStatusResult r && "complete".equals(r.getStatus())) {
            return "authenticated";
        }
        if (QUOTE_TOOLS.contains(tool)) return "quoted";
        if ("executeConfirmedAction".equals(tool)) return "confirmed";
        if (DEPLOY_TOOLS.contains(tool)) return "deployed";
        if (result instanceof ConfirmationRequiredResult) return "staged";
        return null;
    }

    public static Map<String, Object> event(McpConnection connection, String tool, String authMode,
                                            String customerId, long durationMs, boolean success,
                                            Object result) {
        Map<String, Object> event = new HashMap<>();
        event.put("sessionId", connection.id()); // backend drops events without it
        event.put("tenantId", "osir");
        event.put("tool", tool);
        event.put("authMode", authMode);
        if (customerId != null) event.put("customerId", customerId);
        event.put("durationMs", (int) Math.min(durationMs, Integer.MAX_VALUE));
        event.put("outcome", success ? "success" : "error");
        event.put("staged", result instanceof ConfirmationRequiredResult);
        event.put("confirmed", success && "executeConfirmedAction".equals(tool));
        String stage = stage(tool, success, result);
        if (stage != null) event.put("stage", stage);
        try {
            var impl = connection.initialRequest() != null ? connection.initialRequest().implementation() : null;
            if (impl != null) {
                if (impl.name() != null) event.put("clientName", impl.name());
                if (impl.version() != null) event.put("clientVersion", impl.version());
            }
        } catch (Exception ignored) {
            // client info is best-effort
        }
        return event;
    }
}
