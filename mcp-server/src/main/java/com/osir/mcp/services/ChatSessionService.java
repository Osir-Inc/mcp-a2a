// ChatSessionService.java - Manages chat sessions with conversation history
package com.osir.mcp.services;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ChatSessionService {

    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();

    public String createSession() {
        String sessionId = "chat_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        ChatSession session = new ChatSession(sessionId);
        sessions.put(sessionId, session);
        return sessionId;
    }

    public ChatSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public void addMessage(String sessionId, String role, String content) {
        ChatSession session = sessions.get(sessionId);
        if (session != null) {
            session.addMessage(role, content);
            session.updateLastActivity();
        }
    }

    public List<ChatMessage> getConversationHistory(String sessionId) {
        ChatSession session = sessions.get(sessionId);
        return session != null ? session.getMessages() : new ArrayList<>();
    }

    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
    }

    public void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        long expireTime = 24 * 60 * 60 * 1000; // 24 hours

        sessions.entrySet().removeIf(entry ->
                (now - entry.getValue().getLastActivity()) > expireTime
        );
    }

    public static class ChatSession {
        private final String sessionId;
        private final List<ChatMessage> messages;
        private long lastActivity;

        public ChatSession(String sessionId) {
            this.sessionId = sessionId;
            this.messages = new ArrayList<>();
            this.lastActivity = System.currentTimeMillis();
        }

        public void addMessage(String role, String content) {
            messages.add(new ChatMessage(role, content, System.currentTimeMillis()));
        }

        public void updateLastActivity() {
            this.lastActivity = System.currentTimeMillis();
        }

        // Getters
        public String getSessionId() { return sessionId; }
        public List<ChatMessage> getMessages() { return new ArrayList<>(messages); }
        public long getLastActivity() { return lastActivity; }
    }

    public static class ChatMessage {
        private final String role;
        private final String content;
        private final long timestamp;

        public ChatMessage(String role, String content, long timestamp) {
            this.role = role;
            this.content = content;
            this.timestamp = timestamp;
        }

        // Getters
        public String getRole() { return role; }
        public String getContent() { return content; }
        public long getTimestamp() { return timestamp; }
    }
}