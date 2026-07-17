package com.osir.mcp.models.suggestion;

public class DomainSuggestionResult {
    private String name;
    private String punyName;
    private String availability;
    private String token;

    public DomainSuggestionResult() {}

    public DomainSuggestionResult(String name, String punyName, String availability, String token) {
        this.name = name;
        this.punyName = punyName;
        this.availability = availability;
        this.token = token;
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPunyName() { return punyName; }
    public void setPunyName(String punyName) { this.punyName = punyName; }

    public String getAvailability() { return availability; }
    public void setAvailability(String availability) { this.availability = availability; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    @Override
    public String toString() {
        return "DomainSuggestionResult{" +
                "name='" + name + '\'' +
                ", availability='" + availability + '\'' +
                ", token='" + token + '\'' +
                '}';
    }
}