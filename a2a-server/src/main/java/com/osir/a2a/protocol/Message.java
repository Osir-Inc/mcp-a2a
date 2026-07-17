package com.osir.a2a.protocol;

import java.util.List;

/**
 * A2A Message - communication unit between agents.
 */
public class Message {

    private String role; // "user" or "agent"
    private List<Part> parts;

    public Message() {}

    public Message(String role, String text) {
        this.role = role;
        this.parts = List.of(new TextPart(text));
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public List<Part> getParts() { return parts; }
    public void setParts(List<Part> parts) { this.parts = parts; }

    public String getTextContent() {
        if (parts == null) return "";
        return parts.stream()
                .filter(p -> p instanceof TextPart)
                .map(p -> ((TextPart) p).getText())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
    }
}
