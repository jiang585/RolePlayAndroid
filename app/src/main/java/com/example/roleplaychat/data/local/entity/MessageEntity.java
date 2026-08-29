package com.example.roleplaychat.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;
import static androidx.room.ForeignKey.SET_NULL;

/**
 * 消息表（架构文档 §6.2 messages）。
 * 唯一索引 (script_id, sequence)；character_id 删除时置空（历史依赖快照展示）。
 */
@Entity(tableName = "messages",
        foreignKeys = {
                @ForeignKey(entity = ScriptEntity.class,
                        parentColumns = "id",
                        childColumns = "script_id",
                        onDelete = CASCADE),
                @ForeignKey(entity = CharacterEntity.class,
                        parentColumns = "id",
                        childColumns = "character_id",
                        onDelete = SET_NULL)
        },
        indices = {
                @Index(value = {"script_id", "sequence"}, unique = true),
                @Index(value = {"script_id", "status"}),
                @Index(value = {"request_id"}),
                @Index(value = {"batch_id"}),
                @Index(value = {"character_id"})
        })
public class MessageEntity {

    @NonNull
    @PrimaryKey
    public String id;

    public String script_id;

    @Nullable
    public String character_id;

    @Nullable
    public String sender_name_snapshot;

    @Nullable
    public String sender_avatar_snapshot;

    @Nullable
    public String appearance_snapshot_json;

    @Nullable
    public String player_role_type;

    public String type;

    public String side;

    public String content;

    public long sequence;

    public long created_at;

    public String status;

    @Nullable
    public String request_id;

    @Nullable
    public String batch_id;

    @Nullable
    public Integer turn_index;

    @Nullable
    public String error_code;

    @Nullable
    public String meta_json;

    public MessageEntity() {
    }

    public MessageEntity(String id, String scriptId, @Nullable String characterId,
                         @Nullable String senderNameSnapshot, @Nullable String senderAvatarSnapshot,
                         @Nullable String appearanceSnapshotJson, @Nullable String playerRoleType,
                         String type, String side, String content, long sequence, long createdAt,
                         String status, @Nullable String requestId, @Nullable String batchId,
                         @Nullable Integer turnIndex, @Nullable String errorCode,
                         @Nullable String metaJson) {
        this.id = id;
        this.script_id = scriptId;
        this.character_id = characterId;
        this.sender_name_snapshot = senderNameSnapshot;
        this.sender_avatar_snapshot = senderAvatarSnapshot;
        this.appearance_snapshot_json = appearanceSnapshotJson;
        this.player_role_type = playerRoleType;
        this.type = type;
        this.side = side;
        this.content = content;
        this.sequence = sequence;
        this.created_at = createdAt;
        this.status = status;
        this.request_id = requestId;
        this.batch_id = batchId;
        this.turn_index = turnIndex;
        this.error_code = errorCode;
        this.meta_json = metaJson;
    }
}
