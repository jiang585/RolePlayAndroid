package com.example.roleplaychat.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;

@Entity(tableName = "character_visual_assets",
        foreignKeys = @ForeignKey(entity = CharacterVisualProfileEntity.class, parentColumns = "id",
                childColumns = "profile_id", onDelete = CASCADE),
        indices = {@Index(value = {"profile_id"})})
public class CharacterVisualAssetEntity {
    @NonNull @PrimaryKey public String id;
    @NonNull public String profile_id;
    @NonNull public String local_path;
    @Nullable public String sha256;
    @NonNull public String asset_type;
    public boolean is_primary;
    public int width;
    public int height;
    public long created_at;

    public CharacterVisualAssetEntity() {}

    public CharacterVisualAssetEntity(String id, String profileId, String localPath, @Nullable String sha256,
                                      String assetType, boolean primary, int width, int height, long createdAt) {
        this.id = id; this.profile_id = profileId; this.local_path = localPath; this.sha256 = sha256;
        this.asset_type = assetType; this.is_primary = primary; this.width = width; this.height = height;
        this.created_at = createdAt;
    }
}
