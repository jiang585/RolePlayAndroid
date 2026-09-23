package com.example.roleplaychat.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;
import static androidx.room.ForeignKey.SET_NULL;

@Entity(tableName = "message_attachments",
        foreignKeys = {
                @ForeignKey(entity = MessageEntity.class, parentColumns = "id", childColumns = "message_id", onDelete = CASCADE),
                @ForeignKey(entity = CharacterEntity.class, parentColumns = "id", childColumns = "character_id", onDelete = SET_NULL),
                @ForeignKey(entity = ImageGenerationJobEntity.class, parentColumns = "id", childColumns = "job_id", onDelete = SET_NULL)
        },
        indices = {
                @Index(value = {"message_id", "sort_index"}),
                @Index(value = {"job_id"}),
                @Index(value = {"character_id"})
        })
public class MessageAttachmentEntity {
    @NonNull @PrimaryKey public String id;
    @NonNull public String message_id;
    @Nullable public String character_id;
    @NonNull public String type;
    @NonNull public String status;
    @Nullable public String local_path;
    @Nullable public String mime_type;
    public int width;
    public int height;
    @Nullable public String job_id;
    public int sort_index;
    public long created_at;

    public MessageAttachmentEntity() {}
}
