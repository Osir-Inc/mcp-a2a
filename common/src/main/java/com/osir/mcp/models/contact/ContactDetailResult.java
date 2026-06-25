package com.osir.mcp.models.contact;

public class ContactDetailResult {
    private boolean success;
    private String message;
    private ContactDetail contact;

    public ContactDetailResult() {}

    public ContactDetailResult(boolean success, String message) {
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
