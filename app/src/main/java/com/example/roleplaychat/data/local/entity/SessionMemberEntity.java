package com.example.roleplaychat.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;
import static androidx.room.ForeignKey.RESTRICT;

/**
 * 群成员表（架构文档 §6.2 session_members）。
 * 业务唯一约束：每剧本最多一个 active PLAYER；(script_id, character_id) 不重复。
 */
@Entity(tableName = "session_members",
        foreignKeys = {
                @ForeignKey(entity = ScriptEntity.class,
                        parentColumns = "id",
                        childColumns = "script_id",
                        onDelete = CASCADE),
                @ForeignKey(entity = CharacterEntity.class,
                        parentColumns = "id",
                        childColumns = "character_id",
                        onDelete = RESTRICT)
        },
        indices = {@Index(value = {"script_id", "character_id"}, unique = true),
                @Index(value = {"script_id", "member_type", "active"}),
                @Index(value = {"character_id"})})
public class SessionMemberEntity {

    public static final String MEMBER_NPC = "NPC";
    public static final String MEMBER_PLAYER = "PLAYER";

    @NonNull
    @PrimaryKey
    public String id;

    public String script_id;

    @Nullable
    public String character_id;

    public String member_type;

    @Nullable
    public String player_role_type;

    public boolean active;

    public long joined_at;

    public SessionMemberEntity() {
    }

    public SessionMemberEntity(String id, String scriptId, @Nullable String characterId,
                               String memberType, @Nullable String playerRoleType,
                               boolean active, long joinedAt) {
        this.id = id;
        this.script_id = scriptId;
        this.character_id = characterId;
        this.member_type = memberType;
        this.player_role_type = playerRoleType;
        this.active = active;
        this.joined_at = joinedAt;
    }
}
