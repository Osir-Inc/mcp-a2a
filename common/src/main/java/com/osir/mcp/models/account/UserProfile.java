package com.osir.mcp.models.account;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Maps /v1/customers/me (CustomerDTO).
 * Top-level: id, customer, details (nested), balance (nested), totalDomains.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfile {

    @JsonAlias({"id", "customer"})
    private String customerId;

    private ContactDetails details;

    private BalanceInfo balance;

    // totalDomains from CustomerDTO; vpsCount is not part of the profile endpoint
    @JsonProperty("totalDomains")
    private int domainCount;

    private int vpsCount;

    public UserProfile() {}

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public ContactDetails getDetails() { return details; }
    public void setDetails(ContactDetails details) { this.details = details; }

    public BalanceInfo getBalance() { return balance; }
    public void setBalance(BalanceInfo balance) { this.balance = balance; }

    public int getDomainCount() { return domainCount; }
    public void setDomainCount(int domainCount) { this.domainCount = domainCount; }

    public int getVpsCount() { return vpsCount; }
    public void setVpsCount(int vpsCount) { this.vpsCount = vpsCount; }

    // Convenience passthrough — used by AccountService
    public String getName() { return details != null ? details.getName() : null; }
    public String getEmail() { return details != null ? details.getEmail() : null; }
    public String getOrganization() { return details != null ? details.getOrganization() : null; }
    public String getCurrency() { return balance != null ? balance.getCurrency() : null; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContactDetails {
        private String name;
        private String surname;
        private String email;
        private String organization;

        public ContactDetails() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getSurname() { return surname; }
        public void setSurname(String surname) { this.surname = surname; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getOrganization() { return organization; }
        public void setOrganization(String organization) { this.organization = organization; }
    }
}
