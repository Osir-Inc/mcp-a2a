package com.osir.mcp.models;

public class AuthStatusResult {
    private boolean authenticated;
    private String username;
    private Long tokenExpiresIn;

    public AuthStatusResult(boolean authenticated, String username, Long tokenExpiresIn) {
        this.authenticated = authenticated;
        this.username = username;
        this.tokenExpiresIn = tokenExpiresIn;
    }

    // Getters and setters
    public boolean isAuthenticated() { return authenticated; }
    public void setAuthenticated(boolean authenticated) { this.authenticated = authenticated; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Long getTokenExpiresIn() { return tokenExpiresIn; }
    public void setTokenExpiresIn(Long tokenExpiresIn) { this.tokenExpiresIn = tokenExpiresIn; }
}
