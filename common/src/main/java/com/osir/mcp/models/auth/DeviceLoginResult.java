package com.osir.mcp.models.auth;

public class DeviceLoginResult {
    private boolean success;
    private String message;
    private String deviceCode;
    private String userCode;
    private String verificationUri;
    private String verificationUriComplete;
    private int expiresIn;
    private int interval;

    public DeviceLoginResult() {}

    public DeviceLoginResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public DeviceLoginResult(boolean success, String message, String deviceCode, String userCode,
                             String verificationUri, String verificationUriComplete,
                             int expiresIn, int interval) {
        this.success = success;
        this.message = message;
        this.deviceCode = deviceCode;
        this.userCode = userCode;
        this.verificationUri = verificationUri;
        this.verificationUriComplete = verificationUriComplete;
        this.expiresIn = expiresIn;
        this.interval = interval;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

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
