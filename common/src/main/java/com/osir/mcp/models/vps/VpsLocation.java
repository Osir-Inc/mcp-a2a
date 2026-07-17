package com.osir.mcp.models.vps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VpsLocation {
    private String id;
    private String city;
    private String countryName;
    private String countryCode;
    @JsonProperty("displayLabel")
    private String displayName;
    private String flagEmoji;

    public VpsLocation() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getCountryName() { return countryName; }
    public void setCountryName(String countryName) { this.countryName = countryName; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getFlagEmoji() { return flagEmoji; }
    public void setFlagEmoji(String flagEmoji) { this.flagEmoji = flagEmoji; }
}
