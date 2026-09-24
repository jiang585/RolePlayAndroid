package com.example.roleplaychat.domain.repository;

import androidx.annotation.Nullable;

import com.example.roleplaychat.domain.model.ImageGenerationRequest;
import com.example.roleplaychat.domain.model.ImageGenerationStatus;
import com.example.roleplaychat.domain.model.HuajingPairingResult;

import java.io.File;
import java.io.IOException;

public interface ImageGenerationGateway {
    HuajingPairingResult claimPairing(String baseUrl, String pairingCode, String deviceName, String appInstanceId) throws IOException;
    ImageGenerationStatus health() throws IOException;
    String uploadAsset(File file, String sha256) throws IOException;
    ImageGenerationStatus create(ImageGenerationRequest request, String[] assetIds) throws IOException;
    ImageGenerationStatus status(String jobId) throws IOException;
    File download(String assetId, File destination) throws IOException;
    boolean isConfigured();
    String getBaseUrl();
    void updateBaseUrl(String baseUrl);
    void configure(String baseUrl, String deviceId, String accessToken);
    void clearConfiguration();
}
