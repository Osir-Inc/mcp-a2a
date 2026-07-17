package com.osir.mcp.security;

import java.util.concurrent.Callable;

public record PendingAction(
        String actionId,
        String toolName,
        String summary,
        String connectionId,
        DestructiveOpRateLimiter.Bucket bucket,
        long expiresAt,
        Callable<Object> action
) {}
