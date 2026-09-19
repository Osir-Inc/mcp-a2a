package com.osir.mcp.models.deploy;

import java.util.List;
import java.util.Map;

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
     *
     * <p>{@code reason} is C2's stable machine code for a FAILED/REFUSED move ({@link C2Reason}),
     * {@code retryable} whether the same call can succeed with nothing changed, and {@code params}
     * the structured facts. All three are null on rows C2 wrote before 2026-09-19. Branch on
     * {@code reason}, never on {@code detail}, which is prose for humans and may change.
     * Contract: app.osir.deploy docs/spec_c2_reason_codes.md.
     */
    public record OwnedMoveDto(String state, String stage, String detail, String since,
                               String reason, Boolean retryable, Map<String, String> params) {

        /** What C2 stored for a key refusal before reason codes (2026-09-19, pre-fb9a8c4). Stored
         *  rows are frozen, so unlike live prose this cannot be reworded under us. Only consulted
         *  when a row has no reason; such a row is replaced by the next move attempt. */
        private static final String LEGACY_KEY_REFUSED_TEXT = "refused the osir deploy key";

        public OwnedMoveDto(String state, String stage, String detail, String since) {
            this(state, stage, detail, since, null, null, null);
        }

        /** The box rejects the platform SSH key: no retry can pass until the user changes the box. */
        public boolean keyRefused() {
            if (reason != null) {
                return C2Reason.BOX_KEY_REFUSED.equals(reason);
            }
            return detail != null && detail.toLowerCase().contains(LEGACY_KEY_REFUSED_TEXT);
        }

        /** Another program holds the web ports on the box: the user must free them first. */
        public boolean portsInUse() {
            return C2Reason.BOX_PORTS_IN_USE.equals(reason);
        }

        public String param(String key) {
            return params == null ? null : params.get(key);
        }
    }

    /** C2's status payload. {@code ownedInstanceId}/{@code boxIp} are set once a move onto a
     *  customer-owned VPS has started — the DURABLE answer to "does this app already have a box?",
     *  which is what keeps a retry from ordering a second one. {@code ownedMove} answers the other
     *  half, "is it still going?", which tier/status cannot: both stay instant/READY throughout. */
    public record StatusEnvelope(AppDto app, DeploymentDto deployment, HealthDto health,
                                 List<RecentErrorDto> recentErrors, QaDto qa,
                                 String ownedInstanceId, String boxIp, OwnedMoveDto ownedMove) {
    }

    /**
     * C2's error body (CONTRACTS §8): {@code {"error": {code, reason, message, retryable, params, ref}}}.
     * {@code code} is the coarse class, {@code reason} the specific one ({@link C2Reason}); branch on
     * those and {@code retryable}, show {@code message}. Also stands for a failure with no C2 body
     * (5xx, transport), then with only {@code message} and {@code retryable} set.
     */
    public record C2Error(String code, String reason, String message, Boolean retryable,
                          Map<String, String> params, String ref) {
        public static C2Error of(String message, Boolean retryable) {
            return new C2Error(null, null, message, retryable, null, null);
        }
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
