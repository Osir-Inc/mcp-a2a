package com.osir.mcp.models.contact;

import java.util.List;

public class ContactListResult {
    private boolean success;
    private String message;
    private List<ContactDetail> contacts;

    public ContactListResult() {}

    public ContactListResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<ContactDetail> getContacts() { return contacts; }
    public void setContacts(List<ContactDetail> contacts) { this.contacts = contacts; }
}
