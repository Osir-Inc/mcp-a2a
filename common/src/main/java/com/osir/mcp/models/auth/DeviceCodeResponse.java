package com.osir.mcp.models.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeviceCodeResponse {
    @JsonProperty("device_code")
    private String deviceCode;

    @JsonProperty("user_code")
    private String userCode;

    @JsonProperty("verification_uri")
    private String verificationUri;

    @JsonProperty("verification_uri_complete")
    private String verificationUriComplete;

    @JsonProperty("expires_in")
    private int expiresIn;

    @JsonProperty("interval")
    private int interval;

    public DeviceCodeResponse() {}

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }

    public String getUserCode() { return userCode; }
    public void setUserCode(String userCode) { this.userCode = userCode; }

    public String getVerificationUri() { return verificationUri; }
    public void setVerificationUri(String verificationUri) { this.verificationUri = verificationUri; }

    public String getVerificationUriComplete() { return verificationUriComplete; }
    public void setVerificationUriComplete(String verificationUriComplete) { this.verificationUriComplete = verificationUriComplete; }

    public int getExpiresIn() { return expiresIn; }
    public void setExpiresIn(int expiresIn) { this.expiresIn = expiresIn; }

    public int getInterval() { return interval; }
    public void setInterval(int interval) { this.interval = interval; }
}
