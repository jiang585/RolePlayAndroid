package com.example.roleplaychat.domain.model;

import androidx.annotation.Nullable;

public final class CharacterVisualAsset {
    private final String id;
    private final String profileId;
    private final String localPath;
    @Nullable private final String sha256;
    private final String assetType;
    private final boolean primary;
    private final int width;
    private final int height;
    private final long createdAt;

    public CharacterVisualAsset(String id, String profileId, String localPath, @Nullable String sha256,
                                String assetType, boolean primary, int width, int height, long createdAt) {
        this.id = id; this.profileId = profileId; this.localPath = localPath; this.sha256 = sha256;
        this.assetType = assetType; this.primary = primary; this.width = width; this.height = height; this.createdAt = createdAt;
    }
    public String getId() { return id; }
    public String getProfileId() { return profileId; }
    public String getLocalPath() { return localPath; }
    @Nullable public String getSha256() { return sha256; }
    public String getAssetType() { return assetType; }
    public boolean isPrimary() { return primary; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public long getCreatedAt() { return createdAt; }
}
