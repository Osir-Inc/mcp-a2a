package com.osir.a2a.resources;

import com.osir.mcp.models.*;
import com.osir.mcp.services.DomainService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
class A2AResourceTest {

    @InjectMock
    DomainService domainService;

    @Test
    void taskSend_checkAvailability_returnsCompleted() {
        when(domainService.checkAvailability("example.com"))
                .thenReturn(new DomainAvailabilityResult("example.com", true, "Domain is available"));

        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "jsonrpc": "2.0",
                    "id": "test-1",
                    "method": "tasks/send",
                    "params": {
                        "id": "mock-test-1",
                        "skill": "check_availability",
                        "agent": "domain-agent",
                        "message": {
                            "role": "user",
                            "parts": [{"type": "text", "text": "example.com"}]
                        }
                    }
                }
            """)
        .when()
            .post("/a2a")
        .then()
            .statusCode(200)
            .body("jsonrpc", equalTo("2.0"))
            .body("id", equalTo("test-1"))
            .body("result.id", equalTo("mock-test-1"))
            .body("result.status", equalTo("completed"))
            .body("result.artifacts", not(empty()));
    }

    @Test
    void taskSend_listDomains_returnsCompleted() {
        when(domainService.getUserDomains())
                .thenReturn(new UserDomainsResult(true, "3 domains found"));

        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "jsonrpc": "2.0",
                    "id": "test-list",
                    "method": "tasks/send",
                    "params": {
                        "skill": "list_domains",
                        "agent": "domain-agent",
                        "message": {
                            "role": "user",
                            "parts": [{"type": "text", "text": "list my domains"}]
                        }
                    }
                }
            """)
        .when()
            .post("/a2a")
        .then()
            .statusCode(200)
            .body("result.status", equalTo("completed"));
    }

    @Test
    void taskSend_missingDomain_returnsInputRequired() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "jsonrpc": "2.0",
                    "id": "test-2",
                    "method": "tasks/send",
                    "params": {
                        "skill": "check_availability",
                        "agent": "domain-agent",
                        "message": {
                            "role": "user",
                            "parts": [{"type": "text", "text": "check availability"}]
                        }
                    }
                }
            """)
        .when()
            .post("/a2a")
        .then()
            .statusCode(200)
            .body("result.status", equalTo("input-required"));
    }

    @Test
    void taskSend_missingMessage_returnsError() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "jsonrpc": "2.0",
                    "id": "test-3",
                    "method": "tasks/send",
                    "params": { "id": "test-no-msg" }
                }
            """)
        .when()
            .post("/a2a")
        .then()
            .statusCode(200)
            .body("error.code", equalTo(-32602))
            .body("error.message", containsString("Missing message"));
    }

    @Test
    void taskSend_missingParams_returnsError() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "jsonrpc": "2.0",
                    "id": "test-4",
                    "method": "tasks/send"
                }
            """)
        .when()
            .post("/a2a")
        .then()
            .statusCode(200)
            .body("error.code", equalTo(-32602));
    }

    @Test
    void taskGet_nonexistent_returnsNotFound() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "jsonrpc": "2.0",
                    "id": "test-5",
                    "method": "tasks/get",
                    "params": { "id": "nonexistent-task-xyz" }
                }
            """)
        .when()
            .post("/a2a")
        .then()
            .statusCode(200)
            .body("error.code", equalTo(-32001));
    }

    @Test
    void taskCancel_nonexistent_returnsError() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "jsonrpc": "2.0",
                    "id": "test-6",
                    "method": "tasks/cancel",
                    "params": { "id": "nonexistent-task-xyz" }
                }
            """)
        .when()
            .post("/a2a")
        .then()
            .statusCode(200)
            .body("error.code", equalTo(-32002));
    }

    @Test
    void invalidJsonRpcVersion_returnsError() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "jsonrpc": "1.0", "id": "test-7", "method": "tasks/send", "params": {} }
            """)
        .when()
            .post("/a2a")
        .then()
            .statusCode(200)
            .body("error.code", equalTo(-32600));
    }

    @Test
    void unknownMethod_returnsMethodNotFound() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "jsonrpc": "2.0", "id": "test-8", "method": "tasks/unknown", "params": {} }
            """)
        .when()
            .post("/a2a")
        .then()
            .statusCode(200)
            .body("error.code", equalTo(-32601));
    }

    @Test
    void taskSend_thenGet_roundtrip() {
        when(domainService.getUserDomains())
                .thenReturn(new UserDomainsResult(true, "OK"));

        // Send
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "jsonrpc": "2.0",
                    "id": "send-rt",
                    "method": "tasks/send",
                    "params": {
                        "id": "roundtrip-mock",
                        "skill": "list_domains",
                        "agent": "domain-agent",
                        "message": { "role": "user", "parts": [{"type": "text", "text": "list"}] }
                    }
                }
            """)
        .when().post("/a2a")
        .then().statusCode(200).body("result.id", equalTo("roundtrip-mock"));

        // Get
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "jsonrpc": "2.0", "id": "get-rt", "method": "tasks/get", "params": { "id": "roundtrip-mock" } }
            """)
        .when().post("/a2a")
        .then()
            .statusCode(200)
            .body("result.id", equalTo("roundtrip-mock"))
            .body("result.status", equalTo("completed"));
    }
}
