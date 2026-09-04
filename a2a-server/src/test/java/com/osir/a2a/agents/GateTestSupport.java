package com.osir.a2a.agents;

import com.osir.a2a.protocol.A2ATask;
import com.osir.a2a.protocol.Artifact;
import com.osir.a2a.protocol.DataPart;
import com.osir.a2a.protocol.Part;
import com.osir.a2a.security.ConfirmationGate;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.services.AuthContext;
import com.osir.mcp.services.AuthService;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wires a REAL {@link ConfirmationGate} into an agent under unit test — there is no CDI container
 * here, and a mocked gate would only prove the mock returns what it was told to. The stage → confirm
 * round trip is the thing worth testing.
 */
final class GateTestSupport {

    static final String SUB = "user-1";

    private GateTestSupport() {
    }

    /** Authenticated caller: staging refuses outright without a session, so this is the useful default. */
    static AuthContext wire(BaseSpecialistAgent agent) {
        return wire(agent, "test-token", SUB);
    }

    /** No session at all: staging must refuse outright rather than run. */
    static void wireUnauthenticated(BaseSpecialistAgent agent) {
        AuthService authService = mock(AuthService.class);
        agent.confirmationGate = ConfirmationGate.forTesting(authService, new AuthContext(),
                new DestructiveOpRateLimiter());
    }

    static AuthContext wire(BaseSpecialistAgent agent, String token, String sub) {
        AuthService authService = mock(AuthService.class);
        AuthContext context = new AuthContext();
        context.setTokenOverride("Bearer " + token);
        when(authService.parseJwtClaims(token)).thenReturn(Map.of("sub", sub));
        agent.confirmationGate = ConfirmationGate.forTesting(authService, context, new DestructiveOpRateLimiter());
        return context;
    }

    /** The actionId the agent staged on this task, or null if it staged nothing. */
    static String stagedActionId(A2ATask task) {
        for (Artifact artifact : task.getArtifacts()) {
            if (!ConfirmationGate.PENDING_ARTIFACT.equals(artifact.getName())) {
                continue;
            }
            for (Part part : artifact.getParts()) {
                if (part instanceof DataPart data) {
                    return String.valueOf(data.getData().get("actionId"));
                }
            }
        }
        return null;
    }

    /** What a caller does to confirm: same task, the actionId it was handed, nothing else. */
    static A2ATask confirmOn(A2ATask task) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("skill", ConfirmationGate.CONFIRM_SKILL);
        metadata.put("actionId", stagedActionId(task));
        task.setMetadata(metadata);
        return task;
    }
}
