package com.osir.mcp.security;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/** Prod shape: global challenge off (/mcp/http = "No sign-in"), /mcp/oauth still OAuth-gated. */
@QuarkusTest
@TestProfile(McpOAuthPathTest.ChallengeOff.class)
class McpOAuthPathTest {

    public static class ChallengeOff implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("mcp.oauth.challenge-enabled", "false");
        }
    }

    private static final String INIT = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{"
            + "\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"t\",\"version\":\"1\"}}}";

    @Test
    void oauthPathChallengesWithPathSpecificMetadata() {
        given().contentType("application/json").accept("application/json, text/event-stream").body(INIT)
                .post("/mcp/oauth")
                .then().statusCode(401)
                .header("WWW-Authenticate", endsWith("/.well-known/oauth-protected-resource/mcp/oauth\""));
    }

    @Test
    void oauthPathWithBearerReachesMcp() {
        given().header("Authorization", "Bearer x").contentType("application/json")
                .accept("application/json, text/event-stream").body(INIT)
                .post("/mcp/oauth")
                .then().statusCode(200);
    }

    @Test
    void noSignInPathStaysOpen() {
        given().contentType("application/json").accept("application/json, text/event-stream").body(INIT)
                .post("/mcp/http")
                .then().statusCode(200);
    }

    @Test
    void metadataMatchesOAuthPathAndDropsDcr() {
        given().get("/.well-known/oauth-protected-resource/mcp/oauth")
                .then().statusCode(200).body("resource", endsWith("/mcp/oauth"));
        given().get("/.well-known/oauth-authorization-server")
                .then().statusCode(200).body("$", not(hasKey("registration_endpoint")));
    }
}
