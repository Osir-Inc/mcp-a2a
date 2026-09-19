package com.osir.mcp.models.deploy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.mcp.models.deploy.DeployDtos.StatusEnvelope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the C2 status contract the MCP deserializes (impl_c2_move_to_owned.md §2): the field NAMES
 * of the owned-box fields, and that a field C2 adds later is ignored rather than fatal.
 *
 * <p>The mapper mirrors the runtime: Quarkus's ObjectMapper has FAIL_ON_UNKNOWN_PROPERTIES disabled
 * by default (quarkus.jackson.fail-on-unknown-properties, @WithDefault "false"), and neither module
 * sets it — which is why C2 could add {@code ownedMove} to a live response without breaking
 * getStatus on the deployed MCP.
 */
class StatusEnvelopeParsingTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    void parsesOwnedMoveAndIgnoresFieldsWeDoNotKnowYet() throws Exception {
        String json = """
                {
                  "app": {"appId": "sole-step-shoes", "name": "sole-step-shoes", "tier": "instant",
                          "status": "READY", "liveUrl": "https://sole-step-shoes.osir.app"},
                  "deployment": {"deploymentId": "dep_synthetic", "state": "READY"},
                  "health": {"state": "HEALTHY"},
                  "recentErrors": [],
                  "qa": null,
                  "ownedInstanceId": "76124ac7-0000-0000-0000-000000000000",
                  "boxIp": "173.208.224.36",
                  "ownedMove": {"state": "MOVING", "stage": "OWNED_SHIPPING_IMAGE",
                                "detail": "shipping image to 173.208.224.36",
                                "since": "2026-09-04T09:12:00Z"},
                  "somethingC2AddsTomorrow": {"nested": true}
                }
                """;

        StatusEnvelope e = mapper.readValue(json, StatusEnvelope.class);

        assertEquals("76124ac7-0000-0000-0000-000000000000", e.ownedInstanceId());
        assertEquals("173.208.224.36", e.boxIp());
        assertNotNull(e.ownedMove());
        assertEquals("MOVING", e.ownedMove().state());
        assertEquals("OWNED_SHIPPING_IMAGE", e.ownedMove().stage());
        assertEquals("2026-09-04T09:12:00Z", e.ownedMove().since());
    }

    @Test
    void parsesReasonCodeAndParams() throws Exception {
        // Wire shape agreed in app.osir.deploy docs/spec_c2_reason_codes.md §3.
        String json = """
                {"app": {"appId": "a", "name": "a", "tier": "instant", "status": "READY"},
                 "ownedMove": {"state": "FAILED", "stage": "OWNED_PREPPING_BOX",
                               "detail": "The VPS refused the Osir deploy key.",
                               "since": "2026-09-19T10:00:00Z",
                               "reason": "BOX_KEY_REFUSED", "retryable": false,
                               "params": {"ip": "1.2.3.4", "publicKey": "ssh-ed25519 AAAAx osir-deploy"}}}
                """;

        DeployDtos.OwnedMoveDto move = mapper.readValue(json, StatusEnvelope.class).ownedMove();

        assertEquals("BOX_KEY_REFUSED", move.reason());
        assertEquals("1.2.3.4", move.param("ip"));
        assertTrue(move.keyRefused());
    }

    @Test
    void ownedMoveIsNullWhenNoMoveWasEverAttempted() throws Exception {
        String json = """
                {"app": {"appId": "plain-app", "name": "plain-app", "tier": "instant", "status": "READY"},
                 "recentErrors": []}
                """;

        StatusEnvelope e = mapper.readValue(json, StatusEnvelope.class);

        assertNull(e.ownedMove());
        assertNull(e.ownedInstanceId());
    }
}
