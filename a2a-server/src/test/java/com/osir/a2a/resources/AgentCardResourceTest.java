package com.osir.a2a.resources;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AgentCardResourceTest {

    @Test
    void agentCard_returnsValidCard() {
        given()
        .when()
            .get("/.well-known/agent.json")
        .then()
            .statusCode(200)
            .body("name", equalTo("OSIR Agent Platform"))
            .body("version", equalTo("1.0.0"))
            .body("url", endsWith("/a2a"))          // absolute now, e.g. http://localhost:8081/a2a
            .body("url", startsWith("http"))
            .body("capabilities.streaming", equalTo(true))
            .body("documentationUrl", not(emptyOrNullString()))
            .body("skills", not(empty()));
    }

    @Test
    void listAgents_returnsAllAgents() {
        given()
        .when()
            .get("/.well-known/agents")
        .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(6))  // 6 specialists + orchestrator
            .body("name", hasItem("OSIR Domain Agent"))
            .body("name", hasItem("OSIR DNS Agent"))
            .body("name", hasItem("OSIR Billing Agent"));
    }
}
