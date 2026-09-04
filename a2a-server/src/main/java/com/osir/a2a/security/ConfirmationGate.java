package com.osir.a2a.security;

import com.osir.a2a.protocol.A2ATask;
import com.osir.a2a.protocol.Artifact;
import com.osir.a2a.protocol.DataPart;
import com.osir.a2a.protocol.Message;
import com.osir.a2a.protocol.Part;
import com.osir.a2a.protocol.TaskState;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.DestructiveOpRateLimiter.Bucket;
import com.osir.mcp.services.AuthContext;
import com.osir.mcp.services.AuthService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Two-step confirmation for A2A skills that spend money or destroy data — the A2A half of the gate
 * every MCP tool already has (docs/A2A-CONFIRMATION-GATE-SPEC.md, Layer A).
 *
 * <p><b>Read this before treating it as a security control.</b> A confirmation round-trip does NOT
 * stop an unattended agent: the caller holding the actionId simply sends it back. What it does buy
 * is real but narrower — an audit trail with the summary, a single-use claim with an expiry, a
 * caller match, rate limiting that caps a runaway loop, and the summary sitting in the task history
 * where a human reviewing the conversation will see it. The controls that actually stop an
 * unattended caller are token scopes and a backend spend cap, neither of which lives here.
 *
 * <p><b>Why this is not {@code PendingActionStore}.</b> MCP tool calls are stateless, so that store
 * keys staged work by an out-of-band actionId and holds a {@link java.util.concurrent.Callable} —
 * which is why a restart forgets every staged action. A2A tasks are stateful and persisted, so a
 * staged action is recorded ON the task as an artifact, as DATA (skill + frozen parameters), and
 * survives a restart with it. Freezing parameters as data rather than a closure is also what makes
 * the "confirm cannot change the deal" rule enforceable.
 */
@ApplicationScoped
public class ConfirmationGate {

    private static final Logger LOG = Logger.getLogger(ConfirmationGate.class);

    /** Skill a caller sends to confirm. Mirrors the MCP tool name so both transports read alike. */
    public static final String CONFIRM_SKILL = "execute_confirmed_action";

    /** Artifact name a staged action is recorded under. Public so callers can find it in the task. */
    public static final String PENDING_ARTIFACT = "confirmation-required";
    static final String CONSUMED_ARTIFACT = "confirmation-consumed";

    /** Same window as the MCP gate. Long enough for a human to read, short enough to not linger. */
    static final long TTL_MS = 5 * 60_000;

    @Inject AuthService authService;
    @Inject AuthContext authContext;
    @Inject DestructiveOpRateLimiter rateLimiter;

    /** A staged action, exactly as it was frozen at stage time. */
    public record Pending(String actionId, String agentId, String skill, Map<String, Object> params,
                          String summary, Bucket bucket, String callerId, long expiresAt) {
    }

    /** Either the action to run, or the reason the caller may not run it — never both. */
    public record Claim(Pending action, String error) {
        public boolean ok() {
            return action != null;
        }
    }

    /** For unit tests, which have no CDI container to wire the three dependencies. */
    public static ConfirmationGate forTesting(AuthService authService, AuthContext authContext,
                                              DestructiveOpRateLimiter rateLimiter) {
        ConfirmationGate gate = new ConfirmationGate();
        gate.authService = authService;
        gate.authContext = authContext;
        gate.rateLimiter = rateLimiter;
        return gate;
    }

    /**
     * Record what the caller asked for, tell them what it will cost or destroy, and do nothing.
     * The task goes to INPUT_REQUIRED; the caller continues it with the actionId to go ahead.
     */
    public A2ATask stage(A2ATask task, String agentId, String skill, Map<String, Object> params,
                         String summary, Bucket bucket) {
        String caller = callerId();
        if (caller == null) {
            task.transitionTo(TaskState.FAILED);
            task.addMessage(new Message("agent", "This operation needs an authenticated session. "
                    + "Send the request again with the account's bearer token. Nothing was done."));
            return task;
        }

        String actionId = "a2a_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long expiresAt = System.currentTimeMillis() + TTL_MS;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("actionId", actionId);
        data.put("agentId", agentId);
        data.put("skill", skill);
        data.put("summary", summary);
        data.put("bucket", bucket.name());
        data.put("callerId", caller);
        data.put("expiresAt", expiresAt);
        data.put("expiresInSeconds", TTL_MS / 1000);
        // Frozen HERE, and read back from HERE at confirm time. The confirm message carries only the
        // actionId, so a caller cannot stage something harmless and confirm something else.
        data.put("params", params == null ? Map.of() : params);
        task.addArtifact(Artifact.ofData(PENDING_ARTIFACT, data));

        task.addMessage(new Message("agent", summary + "\n\nNOTHING HAS HAPPENED YET. Show this to the "
                + "person who owns the account. To go ahead, send another message on this same task with "
                + "metadata {\"skill\": \"" + CONFIRM_SKILL + "\", \"actionId\": \"" + actionId + "\"} "
                + "within " + (TTL_MS / 60_000) + " minutes. Doing nothing cancels it."));
        task.transitionTo(TaskState.INPUT_REQUIRED);
        LOG.infof("a2a gate: staged %s on task %s (agent=%s, action=%s)", skill, task.getId(), agentId, actionId);
        return task;
    }

    /** Is this message an attempt to confirm something already staged on the task? */
    public boolean isConfirming(A2ATask task) {
        if (pending(task).isEmpty()) {
            return false;
        }
        return CONFIRM_SKILL.equals(metaString(task, "skill")) || metaString(task, "actionId") != null;
    }

    /**
     * Validate and consume: exists → unconsumed → unexpired → same caller → actionId echoed →
     * within the rate limit. Every rejection is terminal for that staged action except the rate
     * limit, which the caller can wait out.
     */
    public Claim claim(A2ATask task) {
        Optional<Pending> found = pending(task);
        if (found.isEmpty()) {
            return new Claim(null, "There is nothing staged on this task to confirm. Send the original "
                    + "request again to stage it.");
        }
        Pending p = found.get();

        String echoed = metaString(task, "actionId");
        if (echoed != null && !echoed.equals(p.actionId())) {
            return new Claim(null, "That actionId does not match the action staged on this task ("
                    + p.actionId() + "). Nothing was done.");
        }
        if (echoed == null) {
            // The caller must repeat an id it did not choose, so a stray "yes, go ahead" cannot
            // confirm a spend on its own.
            return new Claim(null, "To confirm, include the actionId '" + p.actionId() + "' in the "
                    + "message metadata. Nothing was done.");
        }
        if (System.currentTimeMillis() > p.expiresAt()) {
            return new Claim(null, "That confirmation expired. Send the original request again to stage "
                    + "a fresh one. Nothing was done.");
        }
        String caller = callerId();
        if (caller == null) {
            return new Claim(null, "This operation needs an authenticated session. Nothing was done.");
        }
        if (!caller.equals(p.callerId())) {
            // Different account/token than the one that staged it — one session must not be able to
            // confirm another's staged spend.
            return new Claim(null, "This confirmation belongs to a different session. Nothing was done.");
        }
        if (!rateLimiter.tryAcquire(caller, p.bucket())) {
            return new Claim(null, "Too many " + p.bucket().name().toLowerCase() + " operations in the "
                    + "last minute (limit " + p.bucket().perMinute() + "). Wait a minute and confirm again. "
                    + "Nothing was done.");
        }

        // Single use: mark it consumed BEFORE running, so a replay cannot act twice.
        task.addArtifact(Artifact.ofData(CONSUMED_ARTIFACT, Map.of("actionId", p.actionId())));
        LOG.infof("a2a gate: confirmed %s on task %s (action=%s)", p.skill(), task.getId(), p.actionId());
        return new Claim(p, null);
    }

    /**
     * The agent that staged the live confirmation on this task, if any. A2AResource pins routing to
     * it: a continuation is re-routed by scoring, so "yes, go ahead" would otherwise be handed to
     * whichever agent the words happen to match rather than the one holding the staged action.
     */
    public Optional<String> pendingAgentId(A2ATask task) {
        return pending(task).map(Pending::agentId);
    }

    /** The newest staged action on the task that has not been consumed. */
    private Optional<Pending> pending(A2ATask task) {
        List<Artifact> artifacts = task.getArtifacts();
        if (artifacts == null || artifacts.isEmpty()) {
            return Optional.empty();
        }
        Pending newest = null;
        java.util.Set<String> consumed = new java.util.HashSet<>();
        synchronized (artifacts) {
            for (Artifact a : artifacts) {
                Map<String, Object> data = dataOf(a);
                if (data == null) {
                    continue;
                }
                if (CONSUMED_ARTIFACT.equals(a.getName())) {
                    consumed.add(String.valueOf(data.get("actionId")));
                } else if (PENDING_ARTIFACT.equals(a.getName())) {
                    Pending p = toPending(data);
                    if (p != null) {
                        newest = p;
                    }
                }
            }
        }
        return newest == null || consumed.contains(newest.actionId()) ? Optional.empty() : Optional.of(newest);
    }

    @SuppressWarnings("unchecked")
    private Pending toPending(Map<String, Object> data) {
        try {
            Object params = data.get("params");
            return new Pending(
                    String.valueOf(data.get("actionId")),
                    String.valueOf(data.get("agentId")),
                    String.valueOf(data.get("skill")),
                    params instanceof Map ? (Map<String, Object>) params : Map.of(),
                    String.valueOf(data.get("summary")),
                    Bucket.valueOf(String.valueOf(data.get("bucket"))),
                    String.valueOf(data.get("callerId")),
                    ((Number) data.get("expiresAt")).longValue());
        } catch (RuntimeException e) {
            // A malformed artifact must not be readable as a valid staged action.
            LOG.warnf("a2a gate: unreadable pending artifact (%s)", e.getMessage());
            return null;
        }
    }

    private static Map<String, Object> dataOf(Artifact artifact) {
        if (artifact == null || artifact.getParts() == null) {
            return null;
        }
        for (Part part : artifact.getParts()) {
            if (part instanceof DataPart d) {
                return d.getData();
            }
        }
        return null;
    }

    private static String metaString(A2ATask task, String key) {
        Map<String, Object> meta = task.getMetadata();
        Object value = meta == null ? null : meta.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    /**
     * The caller's stable subject from the bearer token — NOT a token fingerprint: TokenRefreshService
     * can rotate the token between stage and confirm, and a fingerprint would then reject the caller's
     * own confirmation. The MCP side keys its money rule on the same claim.
     */
    private String callerId() {
        if (!authContext.hasOverride()) {
            return null;
        }
        String token = authContext.getTokenOverride();
        Map<String, Object> claims = authService.parseJwtClaims(
                token.startsWith("Bearer ") ? token.substring(7) : token);
        Object sub = claims == null ? null : claims.get("sub");
        return sub == null || sub.toString().isBlank() ? null : "a2a-" + sub;
    }
}
