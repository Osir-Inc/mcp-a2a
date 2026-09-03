package com.osir.mcp.models.contact;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

// Contact Information Models
public class RegistrantInfo {
    @JsonPropertyDescription("Registrant's legal first name.")
    private String firstName;
    @JsonPropertyDescription("Registrant's legal last name.")
    private String lastName;
    @JsonPropertyDescription("Organization name, if registering on behalf of a company.")
    private String organization;
    @JsonPropertyDescription("Registrant's email address; a valid mailbox that receives ICANN verification.")
    private String email;
    @JsonPropertyDescription("Phone number in +CC.number format, e.g. +355.42123456.")
    private String phone;
    @JsonPropertyDescription("Fax number in +CC.number format, e.g. +355.42123456; rarely used.")
    private String fax;
    @JsonPropertyDescription("Registrant's postal address.")
    private Address address;

    public RegistrantInfo() {}

    public RegistrantInfo(String firstName, String lastName, String email, String phone, Address address) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.address = address;
    }

    // Getters and Setters
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getFax() { return fax; }
    public void setFax(String fax) { this.fax = fax; }

    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }
}
