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

    /** Sync: a move of this app onto a different box is running. Wait it out, retryable. */
    public static final String MOVE_IN_PROGRESS = "MOVE_IN_PROGRESS";

    /** Async: the box rejects the platform SSH key. The user must install it. */
    public static final String BOX_KEY_REFUSED = "BOX_KEY_REFUSED";

    /** Async: something other than our Caddy holds :80/:443 on the box ({@code params.ports}). */
    public static final String BOX_PORTS_IN_USE = "BOX_PORTS_IN_USE";
}
