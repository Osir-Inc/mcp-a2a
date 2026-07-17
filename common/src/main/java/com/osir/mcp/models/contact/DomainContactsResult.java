package com.osir.mcp.models.contact;

public class DomainContactsResult {
    private boolean success;
    private String message;
    private ContactDetail registrant;
    private ContactDetail admin;
    private ContactDetail tech;
    private ContactDetail billing;

    public DomainContactsResult() {}

    public DomainContactsResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public ContactDetail getRegistrant() { return registrant; }
    public void setRegistrant(ContactDetail registrant) { this.registrant = registrant; }

    public ContactDetail getAdmin() { return admin; }
    public void setAdmin(ContactDetail admin) { this.admin = admin; }

    public ContactDetail getTech() { return tech; }
    public void setTech(ContactDetail tech) { this.tech = tech; }

    public ContactDetail getBilling() { return billing; }
    public void setBilling(ContactDetail billing) { this.billing = billing; }
}
