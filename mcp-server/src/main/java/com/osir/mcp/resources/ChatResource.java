// ChatResource.java - Complete REST endpoints for chat interface
package com.osir.mcp.resources;

import com.osir.mcp.services.JsonModeOllamaService;
import com.osir.mcp.services.ChatSessionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/api/chat")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChatResource {

    @Inject
    JsonModeOllamaService jsonModeOllamaService;

    @Inject
    ChatSessionService chatSessionService;

    @POST
    @Path("/session")
    public Response createSession() {
        String sessionId = chatSessionService.createSession();
        return Response.ok(Map.of("sessionId", sessionId)).build();
    }

    @POST
    @Path("/{sessionId}/message")
    public Response sendMessage(@PathParam("sessionId") String sessionId, ChatRequest request) {
        try {
            // Add user message to session
            chatSessionService.addMessage(sessionId, "user", request.getMessage());

            // Process with JSON mode service
            String response = jsonModeOllamaService.processWithJsonMode(request.getMessage(), sessionId);

            // Add assistant response to session
            chatSessionService.addMessage(sessionId, "assistant", response);

            return Response.ok(Map.of(
                    "response", response,
                    "sessionId", sessionId
            )).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to process message: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/{sessionId}/history")
    public Response getHistory(@PathParam("sessionId") String sessionId) {
        List<ChatSessionService.ChatMessage> history = chatSessionService.getConversationHistory(sessionId);
        return Response.ok(Map.of("history", history)).build();
    }

    @DELETE
    @Path("/{sessionId}")
    public Response clearSession(@PathParam("sessionId") String sessionId) {
        chatSessionService.clearSession(sessionId);
        return Response.ok(Map.of("message", "Session cleared")).build();
    }

    // Streaming endpoint for real-time responses
    @POST
    @Path("/{sessionId}/stream")
    @Produces(MediaType.TEXT_PLAIN)
    public Response streamMessage(@PathParam("sessionId") String sessionId, ChatRequest request) {
        try {
            // Add user message to session
            chatSessionService.addMessage(sessionId, "user", request.getMessage());

            return Response.ok((jakarta.ws.rs.core.StreamingOutput) output -> {
                StringBuilder fullResponse = new StringBuilder();

                jsonModeOllamaService.processWithHybridStream(request.getMessage(), sessionId, chunk -> {
                    try {
                        fullResponse.append(chunk);
                        output.write(chunk.getBytes());
                        output.flush();
                    } catch (Exception e) {
                        // Log error but continue
                    }
                });

                // Add complete response to session
                chatSessionService.addMessage(sessionId, "assistant", fullResponse.toString());
            }).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error: " + e.getMessage())
                    .build();
        }
    }

    public static class ChatRequest {
        private String message;

        public ChatRequest() {}

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}