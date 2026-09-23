package com.example.roleplaychat.domain.model;

public final class HuajingPairingResult {
    private final String deviceId;
    private final String accessToken;
    public HuajingPairingResult(String deviceId, String accessToken) {
        this.deviceId = deviceId; this.accessToken = accessToken;
    }
    public String getDeviceId() { return deviceId; }
    public String getAccessToken() { return accessToken; }
}
