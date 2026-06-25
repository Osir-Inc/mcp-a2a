package com.osir.mcp.models.domain;

import com.osir.mcp.models.contact.ContactInfo;
import com.osir.mcp.models.contact.RegistrantInfo;

import java.util.List;

// Domain Registration Models
public class DomainRegistrationRequest {
    private String domain;
    private int years;
    private RegistrantInfo registrant;
    private ContactInfo adminContact;
    private ContactInfo techContact;
    private ContactInfo billingContact;
    private List<String> nameservers;
    private boolean autoRenew;
    private boolean privacyProtection;

    public DomainRegistrationRequest() {}

    public DomainRegistrationRequest(String domain, int years, RegistrantInfo registrant) {
        this.domain = domain;
        this.years = years;
        this.registrant = registrant;
        this.autoRenew = true; // Default to auto-renew
    }

    // Getters and Setters
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public int getYears() { return years; }
    public void setYears(int years) { this.years = years; }

    public RegistrantInfo getRegistrant() { return registrant; }
    public void setRegistrant(RegistrantInfo registrant) { this.registrant = registrant; }

    public ContactInfo getAdminContact() { return adminContact; }
    public void setAdminContact(ContactInfo adminContact) { this.adminContact = adminContact; }

    public ContactInfo getTechContact() { return techContact; }
    public void setTechContact(ContactInfo techContact) { this.techContact = techContact; }

    public ContactInfo getBillingContact() { return billingContact; }
    public void setBillingContact(ContactInfo billingContact) { this.billingContact = billingContact; }

    public List<String> getNameservers() { return nameservers; }
    public void setNameservers(List<String> nameservers) { this.nameservers = nameservers; }

    public boolean isAutoRenew() { return autoRenew; }
    public void setAutoRenew(boolean autoRenew) { this.autoRenew = autoRenew; }

    public boolean isPrivacyProtection() { return privacyProtection; }
    public void setPrivacyProtection(boolean privacyProtection) { this.privacyProtection = privacyProtection; }
}
