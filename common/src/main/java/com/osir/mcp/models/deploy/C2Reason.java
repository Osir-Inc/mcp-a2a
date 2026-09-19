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
}
