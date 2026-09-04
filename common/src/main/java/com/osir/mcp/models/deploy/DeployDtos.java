package com.osir.mcp.models.deploy;

import java.util.List;

/**
 * DTOs for the deploy backend (C2) seam and the LLM-facing tool results. Backend wire envelopes
 * mirror CONTRACTS.md §1/§2; tool-result records carry the success/message shape the other MCP
 * tools use. Unknown backend fields are ignored (Quarkus Jackson fail-on-unknown=false).
 */
public final class DeployDtos {
    private DeployDtos() {
    }

    // ---- Backend wire shapes (deserialized from C2 responses) -------------------------------
    public record AppDto(String appId, String name, String region, String tier, String runtimeClass,
                         String language, String status, String liveUrl, String currentVersionId) {
    }

    public record AppEnvelope(AppDto app) {
    }

    public record AppsEnvelope(List<AppDto> apps) {
    }

    public record HealthDto(String state, String lastSeen, Integer restartsLast24h, String note) {
    }

    public record DeploymentDto(String deploymentId, String state, String message) {
    }

    public record RecentErrorDto(String ts, String message) {
    }

    /** Black-box QA verdict (C8): did the LIVE app actually work, not just deploy. status =
     *  RUNNING|PASSED|FAILED; findings list the concrete problems when it didn't pass. */
    public record QaDto(String status, int httpStatus, List<String> findings, String checkedAt) {
    }

    /**
     * How a move onto a customer-owned box is going. C2 derives this from the audit rows the move
     * writes, so it survives a C2 restart (its in-memory tracker does not, and must not be the
     * source of this answer). Null when no move was ever attempted for the app.
     *
     * <p>{@code state} is MOVING | MOVED | FAILED | REFUSED — and only MOVING means "leave it
     * alone": a repeat call on a FAILED move is how a transient ship failure recovers.
     * {@code stage} is the audit stage (OWNED_PREPPING_BOX, OWNED_SHIPPING_IMAGE, ...).
     */
    public record OwnedMoveDto(String state, String stage, String detail, String since) {
    }

    /** C2's status payload. {@code ownedInstanceId}/{@code boxIp} are set once a move onto a
     *  customer-owned VPS has started — the DURABLE answer to "does this app already have a box?",
     *  which is what keeps a retry from ordering a second one. {@code ownedMove} answers the other
     *  half, "is it still going?", which tier/status cannot: both stay instant/READY throughout. */
    public record StatusEnvelope(AppDto app, DeploymentDto deployment, HealthDto health,
                                 List<RecentErrorDto> recentErrors, QaDto qa,
                                 String ownedInstanceId, String boxIp, OwnedMoveDto ownedMove) {
    }

    public record ConfirmationEnvelope(String confirmationId, String summary) {
    }

    public record UploadEnvelope(String uploadTicket, String putUrl) {
    }

    /** C2's answer to GET /v1/apps/{appId}/source — a short-lived signed download URL. */
    public record SourceEnvelope(String getUrl, String expiresAt) {
    }

    public record AppSourceResult(boolean success, String message, String getUrl,
                                  String expiresAt, String instructions) {
        public static AppSourceResult fail(String msg) {
            return new AppSourceResult(false, msg, null, null, null);
        }
    }

    public record ProvisionDbEnvelope(String secretKey, String message) {
    }

    public record LogsEnvelope(String logs) {
    }

    // ---- Request body to C2 -----------------------------------------------------------------
    public record SourceRefBody(String type, String uploadTicket) {
        public static SourceRefBody inlineArchive(String ticket) {
            return new SourceRefBody("inline_archive", ticket);
        }
    }

    public record DeployAppBody(String name, String language, String region, SourceRefBody source) {
    }

    public record SecretBody(String key, String value) {
    }

    // ---- LLM-facing tool results ------------------------------------------------------------
    public record DeployResult(boolean success, String message, String appId, String liveUrl, String status) {
        public static DeployResult fail(String msg) {
            return new DeployResult(false, msg, null, null, null);
        }
    }

    public record AppListResult(boolean success, String message, List<AppDto> apps) {
        public static AppListResult fail(String msg) {
            return new AppListResult(false, msg, List.of());
        }
    }

    /** {@code ownedInstanceId}/{@code boxIp}: the customer-owned VPS this app is bound to, if any.
     *  {@code ownedMove}: how the move onto it is going (null if none was ever attempted). */
    public record AppStatusResult(boolean success, String message, AppDto app, HealthDto health,
                                  String deploymentState, List<RecentErrorDto> recentErrors, QaDto qa,
                                  String ownedInstanceId, String boxIp, OwnedMoveDto ownedMove) {
        public static AppStatusResult fail(String msg) {
            return new AppStatusResult(false, msg, null, null, null, List.of(), null, null, null, null);
        }
    }

    public record UploadTicketResult(boolean success, String message, String uploadTicket,
                                     String putUrl, String instructions) {
        public static UploadTicketResult fail(String msg) {
            return new UploadTicketResult(false, msg, null, null, null);
        }
    }

    public record DeleteResult(boolean success, String message) {
    }

    public record SetSecretResult(boolean success, String message) {
        public static SetSecretResult fail(String msg) {
            return new SetSecretResult(false, msg);
        }
    }

    public record ProvisionDbResult(boolean success, String message, String secretKey) {
        public static ProvisionDbResult fail(String msg) {
            return new ProvisionDbResult(false, msg, null);
        }
    }

    public record AppLogsResult(boolean success, String message, String logs) {
        public static AppLogsResult fail(String msg) {
            return new AppLogsResult(false, msg, null);
        }
    }
}
