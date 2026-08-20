package com.osir.mcp.models.mail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Deserialized straight from POST .../mailboxes ({account, password}); the service fills
 * success/message/emailAddress/clientSettings.
 *
 * The password appears exactly ONCE — the backend cannot return it again. It must be shown to
 * the user immediately and must never be logged or stored anywhere on this server.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MailboxCreateResult {
    private boolean success;
    private String message;
    private MailAccountInfo account;
    private String password;
    private String emailAddress;
    private String clientSettings;

    public MailboxCreateResult() {}

    public MailboxCreateResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public MailAccountInfo getAccount() { return account; }
    public void setAccount(MailAccountInfo account) { this.account = account; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }

    public String getClientSettings() { return clientSettings; }
    public void setClientSettings(String clientSettings) { this.clientSettings = clientSettings; }
}
