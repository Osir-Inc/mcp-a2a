package com.osir.mcp.security;

import java.util.concurrent.Callable;

/**
 * A staged destructive/financial action awaiting confirmation.
 *
 * {@code owner} is the authenticated user identity (stable across MCP sessions) and gates
 * who may execute the action; {@code connectionId} is kept only as a diagnostic breadcrumb
 * of which connection staged it.
 */
public record PendingAction(
        String actionId,
        String toolName,
        String summary,
        String owner,
        String connectionId,
        DestructiveOpRateLimiter.Bucket bucket,
        long expiresAt,
        Callable<Object> action
) {}
