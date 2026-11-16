package com.osir.mcp.models.deploy;

/**
 * The C2 reason codes C1 branches on: a subset of C2's catalog ({@code com.osir.deploy.error.Reason},
 * pinned there by ReasonCatalogTest). Codes are the contract, C2's messages are not: never branch on
 * message text. A code C1 does not know falls back to {@code retryable}.
 * Contract: app.osir.deploy docs/spec_c2_reason_codes.md.
 */
public final class C2Reason {
    private C2Reason() {
    }

    /** Sync: a move of this app onto a different box is running. Wait it out, retryable. C2 checks
     *  the binding first, so today a different box gets {@link #APP_BOUND_TO_OTHER_BOX} instead. */
    public static final String MOVE_IN_PROGRESS = "MOVE_IN_PROGRESS";

    /** Sync: the app already has an owned box ({@code params.instanceId}); one box per app. Also what
     *  a request for a second box gets WHILE the move onto the first runs (C2 cb00c75). */
    public static final String APP_BOUND_TO_OTHER_BOX = "APP_BOUND_TO_OTHER_BOX";

    /** Async: the box rejects the platform SSH key. The user must install it. */
    public static final String BOX_KEY_REFUSED = "BOX_KEY_REFUSED";

    /** Async: something other than our Caddy holds :80/:443 on the box ({@code params.ports}). */
    public static final String BOX_PORTS_IN_USE = "BOX_PORTS_IN_USE";

    /** Async, agents only: the VPS has less memory than the agent needs ({@code params.memoryMb},
     *  {@code params.requiredMb}). Only a bigger VPS fixes it. */
    public static final String BOX_TOO_SMALL = "BOX_TOO_SMALL";

    /** Sync 409, move / agent install: the instance is the caller's but OSIR has it at a different ip.
     *  Not retryable as sent: the ip must come from listMyVpsInstances (C2 2026-11-16). */
    public static final String BOX_IP_MISMATCH = "BOX_IP_MISMATCH";

    /** What the model is told for {@link #BOX_IP_MISMATCH}. */
    public static String ipMismatch(String instanceId) {
        return "That IP isn't the address of VPS '" + instanceId + "' - use the one from listMyVpsInstances, never "
                + "an IP typed or remembered. Nothing was installed and nothing more was charged.";
    }
}
