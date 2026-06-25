package com.osir.mcp.models.contact;

public class ContactResult {
    private boolean success;
    private String message;
    private ContactDetail contact;

    public ContactResult() {}

    public ContactResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public ContactDetail getContact() { return contact; }
    public void setContact(ContactDetail contact) { this.contact = contact; }
}
