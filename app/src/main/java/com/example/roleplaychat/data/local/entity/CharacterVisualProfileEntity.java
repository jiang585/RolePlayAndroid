package com.example.roleplaychat.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;

@Entity(tableName = "character_visual_profiles",
        foreignKeys = @ForeignKey(entity = CharacterEntity.class, parentColumns = "id",
                childColumns = "character_id", onDelete = CASCADE),
        indices = {@Index(value = {"character_id"}, unique = true)})
public class CharacterVisualProfileEntity {
    @NonNull @PrimaryKey public String id;
    @NonNull public String character_id;
    @NonNull public String status;
    @NonNull public String source;
    public int version;
    @Nullable public String identity_prompt;
    @Nullable public String appearance_json;
    @Nullable public String negative_prompt;
    public long created_at;
    public long updated_at;

    public CharacterVisualProfileEntity() {}

    public CharacterVisualProfileEntity(String id, String characterId, String status, String source,
                                        int version, @Nullable String identityPrompt,
                                        @Nullable String appearanceJson, @Nullable String negativePrompt,
                                        long createdAt, long updatedAt) {
        this.id = id; this.character_id = characterId; this.status = status; this.source = source;
        this.version = version; this.identity_prompt = identityPrompt; this.appearance_json = appearanceJson;
        this.negative_prompt = negativePrompt; this.created_at = createdAt; this.updated_at = updatedAt;
    }
}
