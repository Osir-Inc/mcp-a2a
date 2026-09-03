package com.osir.mcp.models.account;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * POST /v1/public/account (anonymous) — backend v2.11.0 autonomous onboarding.
 * The contact is the PRINCIPAL's ICANN registrant contact, never the agent itself.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateAccountRequest {

    private String email;
    private String password;          // optional
    private String accountType;       // INDIVIDUAL | ORGANIZATION
    private Contact contact;
    private AgentMetadata agentMetadata; // optional, audit only
    private boolean acceptedTerms;
    private String termsVersion;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Contact {
        @JsonPropertyDescription("Principal's legal first name.")
        public String firstName;
        @JsonPropertyDescription("Principal's legal last name.")
        public String lastName;
        @JsonPropertyDescription("Organization name, required for ORGANIZATION accounts.")
        public String organization;
        @JsonPropertyDescription("Contact email address; a valid mailbox that receives ICANN verification.")
        public String email;
        @JsonPropertyDescription("Phone number in +CC.number format, e.g. +355.42123456.")
        public String phone;
        @JsonPropertyDescription("First street address line.")
        public String street1;
        @JsonPropertyDescription("Second street address line, if needed.")
        public String street2;
        @JsonPropertyDescription("City or locality name.")
        public String city;
        @JsonPropertyDescription("State, province, or region name.")
        public String state;
        @JsonPropertyDescription("Postal or ZIP code.")
        public String postalCode;
        @JsonPropertyDescription("2-letter ISO 3166-1 alpha-2 country code, e.g. US.")
        public String country;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AgentMetadata {
        public String name;
        public String vendor;
        public String principal;

        public AgentMetadata() {}

        public AgentMetadata(String name, String vendor, String principal) {
            this.name = name;
            this.vendor = vendor;
            this.principal = principal;
        }
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public Contact getContact() { return contact; }
    public void setContact(Contact contact) { this.contact = contact; }
    public AgentMetadata getAgentMetadata() { return agentMetadata; }
    public void setAgentMetadata(AgentMetadata agentMetadata) { this.agentMetadata = agentMetadata; }
    public boolean isAcceptedTerms() { return acceptedTerms; }
    public void setAcceptedTerms(boolean acceptedTerms) { this.acceptedTerms = acceptedTerms; }
    public String getTermsVersion() { return termsVersion; }
    public void setTermsVersion(String termsVersion) { this.termsVersion = termsVersion; }
}
