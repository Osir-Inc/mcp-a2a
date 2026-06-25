package com.osir.mcp.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.osir.mcp.models.DomainAvailabilityResult;
import com.osir.mcp.models.DomainInfoResult;
import com.osir.mcp.models.UserDomainsResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@ApplicationScoped
public class JsonModeOllamaService {

    private static final Logger LOG = Logger.getLogger(JsonModeOllamaService.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SessionAwareDomainService sessionAwareDomainService;

    @Inject
    SessionAwareAuthService sessionAwareAuthService;

    @ConfigProperty(name = "ollama.base-url", defaultValue = "http://localhost:11434")
    String ollamaBaseUrl;

    @ConfigProperty(name = "ollama.model", defaultValue = "llama3.1:8b")
    String ollamaModel;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final String JSON_SYSTEM_PROMPT = """
            You are a domain management assistant. You MUST respond in valid JSON format only.

            Your response should be a JSON object with this structure:
            {
              "reasoning": "Brief explanation of what you're going to do",
              "action": {
                "type": "tool_call|conversation", 
                "tool_name": "loginWithDevice|checkDeviceLoginStatus|checkDomainAvailability|getDomainInfo|listUserDomains|getAuthStatus|logout",
                "parameters": {"param1": "value1", "param2": "value2"},
                "message": "conversational response if no tool needed"
              }
            }

            TOOL MAPPING RULES:
            - Login/authenticate requests → "loginWithDevice" (no parameters needed)
            - Domain availability questions → "checkDomainAvailability" with domain
            - Domain information requests → "getDomainInfo" with domain  
            - List user domains → "listUserDomains"
            - Check auth status → "getAuthStatus"
            - Logout → "logout"
            - General questions → "conversation" type with message

            EXAMPLES:

            User: "login with username root@host.al and password mypass"
            {
              "reasoning": "User wants to authenticate; start device flow",
              "action": {
                "type": "tool_call",
                "tool_name": "loginWithDevice",
                "parameters": {}
              }
            }

            User: "is osir.com available?"
            {
              "reasoning": "User wants to check domain availability",
              "action": {
                "type": "tool_call",
                "tool_name": "checkDomainAvailability",
                "parameters": {"domain": "osir.com"}
              }
            }

            User: "what is a domain?"
            {
              "reasoning": "User asking general question about domains",
              "action": {
                "type": "conversation",
                "message": "A domain is a human-readable address for websites..."
              }
            }

            CRITICAL: Always respond with valid JSON only. No other text.
            """;

    public String processWithJsonMode(String userMessage, String chatSessionId) {
        try {
            LOG.infof("Processing message in JSON mode for session %s: %s", chatSessionId, userMessage);

            // Call Ollama with JSON mode
            JsonNode responseJson = callOllamaJson(userMessage);

            // Parse and execute the response
            return executeJsonResponse(responseJson, chatSessionId);

        } catch (Exception e) {
            LOG.errorf(e, "Error in JSON mode processing");
            return "❌ I encountered an error processing your request: " + e.getMessage();
        }
    }

    private JsonNode callOllamaJson(String userMessage) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", ollamaModel);
        request.put("stream", false);
        request.put("format", "json"); // Force JSON response

        ArrayNode messages = objectMapper.createArrayNode();
        messages.add(objectMapper.createObjectNode()
                .put("role", "system")
                .put("content", JSON_SYSTEM_PROMPT));
        messages.add(objectMapper.createObjectNode()
                .put("role", "user")
                .put("content", userMessage));
        request.set("messages", messages);

        // Lower temperature for more consistent JSON
        ObjectNode options = objectMapper.createObjectNode();
        options.put("temperature", 0.1);
        options.put("top_p", 0.9);
        request.set("options", options);

        LOG.infof("Calling Ollama at %s with model %s", ollamaBaseUrl, ollamaModel);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ollamaBaseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        LOG.infof("Ollama response status: %d", response.statusCode());

        if (response.statusCode() == 404) {
            LOG.errorf("Model '%s' not found. Available models can be checked with 'ollama list'", ollamaModel);
            throw new RuntimeException("Model '" + ollamaModel + "' not found. Please check 'ollama list' for available models.");
        }

        if (response.statusCode() != 200) {
            LOG.errorf("Ollama API error: %d - %s", response.statusCode(), response.body());
            throw new RuntimeException("Ollama API error: " + response.statusCode() + " - " + response.body());
        }

        JsonNode responseJson = objectMapper.readTree(response.body());
        String content = responseJson.get("message").get("content").asText();

        LOG.infof("Raw JSON response: %s", content);

        // Parse the JSON content
        return objectMapper.readTree(content);
    }

    private String executeJsonResponse(JsonNode responseJson, String chatSessionId) {
        try {
            String reasoning = responseJson.get("reasoning").asText();
            JsonNode action = responseJson.get("action");
            String actionType = action.get("type").asText();

            LOG.infof("LLM reasoning: %s", reasoning);
            LOG.infof("Action type: %s", actionType);

            if ("tool_call".equals(actionType)) {
                String toolName = action.get("tool_name").asText();
                JsonNode parameters = action.get("parameters");

                LOG.infof("Executing tool: %s with params: %s", toolName, parameters);
                return executeToolCall(toolName, parameters, chatSessionId);

            } else if ("conversation".equals(actionType)) {
                return action.get("message").asText();
            } else {
                return "❌ Unknown action type: " + actionType;
            }

        } catch (Exception e) {
            LOG.errorf(e, "Error executing JSON response");
            return "❌ Error processing response: " + e.getMessage();
        }
    }

    private String executeToolCall(String toolName, JsonNode params, String chatSessionId) {
        try {
            switch (toolName) {
                case "authenticateUser":
                    var deviceLogin = sessionAwareAuthService.startDeviceLogin(chatSessionId);
                    if (!deviceLogin.isSuccess()) {
                        return "❌ Could not start login: " + deviceLogin.getMessage();
                    }
                    return "🔐 Open this URL in your browser and enter code **" + deviceLogin.getUserCode()
                            + "** to sign in:\n" + deviceLogin.getVerificationUriComplete()
                            + "\n\nThen call checkDeviceLoginStatus with deviceCode="
                            + deviceLogin.getDeviceCode() + " to complete login.";

                case "checkDomainAvailability":
                    String domain = params.get("domain").asText();
                    LOG.infof("=== DOMAIN AVAILABILITY CHECK ===");
                    LOG.infof("Checking domain: %s for session: %s", domain, chatSessionId);

                    boolean isAuth = sessionAwareAuthService.isAuthenticated(chatSessionId);
                    LOG.infof("Authentication status: %s", isAuth);

                    if (!isAuth) {
                        return "❌ Authentication required. Please login first.";
                    }

                    // Get the token for this session and temporarily set it for the domain service
                    String token = sessionAwareAuthService.getCurrentToken(chatSessionId);
                    LOG.infof("Using token for domain service: %s", token != null ? "Present" : "Missing");

                    // Call domain service with session-aware authentication
                    var domainResult = callDomainServiceWithSession(domain, chatSessionId);
                    LOG.infof("Domain check result: success=%s, available=%s, message=%s",
                            domainResult.isAvailable(), domainResult.isAvailable(), domainResult.getMessage());

                    return domainResult.isAvailable() ?
                            (domainResult.isAvailable() ?
                                    "✅ " + domain + " is available!" :
                                    "❌ " + domain + " is taken.") :
                            "❌ Error: " + domainResult.getMessage();

                case "getDomainInfo":
                    String infoDomain = params.get("domain").asText();
                    if (!sessionAwareAuthService.isAuthenticated(chatSessionId)) {
                        return "❌ Authentication required. Please login first.";
                    }
                    var infoResult = callDomainInfoWithSession(infoDomain, chatSessionId);
                    return infoResult.isSuccess() ?
                            String.format("📄 %s: %s (expires %s)",
                                    infoDomain, infoResult.getStatus(), infoResult.getExpirationDate()) :
                            "❌ " + infoResult.getMessage();

                case "getAuthStatus":
                    var statusResult = sessionAwareAuthService.getAuthStatus(chatSessionId);
                    return statusResult.isAuthenticated() ?
                            "✅ Logged in as: " + statusResult.getUsername() :
                            "❌ Not authenticated";

                case "listUserDomains":
                    if (!sessionAwareAuthService.isAuthenticated(chatSessionId)) {
                        return "❌ Authentication required. Please login first.";
                    }
                    var userResult = callListUserDomainsWithSession(chatSessionId);
                    return userResult.isSuccess() ?
                            "📁 Your domains: " + userResult.getDomains().size() + " registered" :
                            "❌ " + userResult.getMessage();

                case "logout":
                    sessionAwareAuthService.logout(chatSessionId);
                    return "✅ Logged out successfully";

                default:
                    return "❌ Unknown tool: " + toolName;
            }
        } catch (Exception e) {
            LOG.errorf(e, "Tool execution error");
            return "❌ Error executing " + toolName + ": " + e.getMessage();
        }
    }

    // Helper methods that work with session-aware authentication
    private DomainAvailabilityResult callDomainServiceWithSession(String domain, String chatSessionId) {
        return sessionAwareDomainService.checkAvailability(domain, chatSessionId);
    }

    private DomainInfoResult callDomainInfoWithSession(String domain, String chatSessionId) {
        return sessionAwareDomainService.getDomainInfo(domain, chatSessionId);
    }

    private UserDomainsResult callListUserDomainsWithSession(String chatSessionId) {
        return sessionAwareDomainService.getUserDomains(chatSessionId);
    }

    public void processWithJsonModeStream(String userMessage, String chatSessionId, java.util.function.Consumer<String> onChunk) {
        try {
            LOG.infof("Processing streaming message in JSON mode for session %s", chatSessionId);

            // For streaming, we need to handle it differently since JSON mode requires complete response
            // Option 1: Get complete JSON response first, then stream the execution result
            JsonNode responseJson = callOllamaJson(userMessage);
            String result = executeJsonResponse(responseJson, chatSessionId);

            // Stream the result character by character for better UX
            for (int i = 0; i < result.length(); i++) {
                onChunk.accept(String.valueOf(result.charAt(i)));
                try {
                    Thread.sleep(10); // Small delay for streaming effect
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

        } catch (Exception e) {
            LOG.errorf(e, "Error in JSON mode streaming");
            onChunk.accept("❌ Error processing your request: " + e.getMessage());
        }
    }

    // Alternative: True streaming with fallback to non-JSON for conversation
    public void processWithHybridStream(String userMessage, String chatSessionId, java.util.function.Consumer<String> onChunk) {
        try {
            // First, quickly classify if this needs a tool call
            if (isToolCallNeeded(userMessage)) {
                // Use JSON mode for tool calls (non-streaming)
                JsonNode responseJson = callOllamaJson(userMessage);
                String result = executeJsonResponse(responseJson, chatSessionId);
                onChunk.accept(result);
            } else {
                // Use regular streaming for conversation
                streamConversationalResponse(userMessage, onChunk);
            }

        } catch (Exception e) {
            onChunk.accept("❌ Error: " + e.getMessage());
        }
    }

    private boolean isToolCallNeeded(String message) {
        String lower = message.toLowerCase();
        return lower.contains("login") || lower.contains("authenticate") ||
                lower.contains("check") || lower.contains("available") ||
                lower.contains("domain") || lower.contains("info") ||
                lower.contains("list") || lower.contains("logout");
    }

    private void streamConversationalResponse(String userMessage, java.util.function.Consumer<String> onChunk) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", ollamaModel);
        request.put("stream", true);

        ArrayNode messages = objectMapper.createArrayNode();
        messages.add(objectMapper.createObjectNode()
                .put("role", "system")
                .put("content", "You are a helpful domain management assistant. Provide conversational responses about domain management topics."));
        messages.add(objectMapper.createObjectNode()
                .put("role", "user")
                .put("content", userMessage));
        request.set("messages", messages);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ollamaBaseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        String[] lines = response.body().split("\n");
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                try {
                    JsonNode chunk = objectMapper.readTree(line);
                    if (chunk.has("message") && chunk.get("message").has("content")) {
                        String content = chunk.get("message").get("content").asText();
                        onChunk.accept(content);
                    }
                } catch (Exception e) {
                    // Skip invalid JSON lines
                }
            }
        }
    }
}