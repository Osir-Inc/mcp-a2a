package com.osir.mcp.models.deploy;

/**
 * DTOs for moving an app from the shared tier onto a customer-owned VPS
 * (C2 contract: POST /v1/apps/{appId}/move-to-owned).
 */
public final class MoveToOwnedDtos {

    private MoveToOwnedDtos() {}

    /** Request body for the C2 move-to-owned endpoint. The box is already ordered and built. */
    public record MoveToOwnedBody(String instanceId, String ip, String domain) {
    }

    /**
     * LLM-facing outcome of osirAppMoveToOwned. {@code status} is one of:
     * MOVING (C2 accepted the move and ships asynchronously — poll osirAppStatus until
     * tier reads "owned"), BUILDING (VPS not ready yet — call the tool again to resume),
     * BUILD_FAILED (OS install failed — free rebuild via buildVpsInstance, never re-order),
     * FAILED (validation or backend error).
     */
    public record MoveToOwnedResult(boolean success, String status, String message,
                                    String instanceId, String ipv4, String domain,
                                    Boolean dnsBound, String nextStep) {
        public static MoveToOwnedResult fail(String msg) {
            return new MoveToOwnedResult(false, "FAILED", msg, null, null, null, null, null);
        }
    }
}
