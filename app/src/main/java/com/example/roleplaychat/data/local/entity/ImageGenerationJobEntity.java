package com.example.roleplaychat.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;
import static androidx.room.ForeignKey.SET_NULL;

@Entity(tableName = "image_generation_jobs",
        foreignKeys = {
                @ForeignKey(entity = ScriptEntity.class, parentColumns = "id", childColumns = "script_id", onDelete = CASCADE),
                @ForeignKey(entity = CharacterEntity.class, parentColumns = "id", childColumns = "character_id", onDelete = SET_NULL),
                @ForeignKey(entity = MessageEntity.class, parentColumns = "id", childColumns = "message_id", onDelete = SET_NULL)
        },
        indices = {
                @Index(value = {"client_job_id"}, unique = true),
                @Index(value = {"script_id", "status"}),
                @Index(value = {"character_id"}),
                @Index(value = {"message_id"})
        })
public class ImageGenerationJobEntity {
    @NonNull @PrimaryKey public String id;
    @NonNull public String script_id;
    @Nullable public String character_id;
    @Nullable public String message_id;
    @NonNull public String client_job_id;
    @Nullable public String huajing_job_id;
    @NonNull public String trigger;
    @NonNull public String intent;
    @NonNull public String model;
    @NonNull public String mode;
    @NonNull public String status;
    @Nullable public String prompt_snapshot;
    @Nullable public String reference_snapshot_json;
    @Nullable public String result_asset_id;
    public int retry_count;
    @Nullable public String error_code;
    public long created_at;
    @Nullable public Long started_at;
    @Nullable public Long finished_at;

    public ImageGenerationJobEntity() {}
}
