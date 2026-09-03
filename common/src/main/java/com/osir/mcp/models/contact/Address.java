package com.osir.mcp.models.contact;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class Address {
    @JsonPropertyDescription("First street address line.")
    private String street1;
    @JsonPropertyDescription("Second street address line, if needed.")
    private String street2;
    @JsonPropertyDescription("City or locality name.")
    private String city;
    @JsonPropertyDescription("State, province, or region name.")
    private String state;
    @JsonPropertyDescription("Postal or ZIP code.")
    private String postalCode;
    @JsonPropertyDescription("2-letter ISO 3166-1 alpha-2 country code, e.g. US.")
    private String country;

    public Address() {}

    public Address(String street1, String city, String state, String postalCode, String country) {
        this.street1 = street1;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.country = country;
    }

    // Getters and Setters
    public String getStreet1() { return street1; }
    public void setStreet1(String street1) { this.street1 = street1; }

    public String getStreet2() { return street2; }
    public void setStreet2(String street2) { this.street2 = street2; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
}
