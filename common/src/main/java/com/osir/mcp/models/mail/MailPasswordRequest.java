package com.osir.mcp.models.mail;

/** Body for PUT /v1/hosting/mail/mailboxes/{id}/password. */
public class MailPasswordRequest {
    private String password;

    public MailPasswordRequest() {}

    public MailPasswordRequest(String password) {
        this.password = password;
    }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
