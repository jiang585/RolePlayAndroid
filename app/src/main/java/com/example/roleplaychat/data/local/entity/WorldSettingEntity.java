package com.example.roleplaychat.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;

/**
 * 世界观表（架构文档 §6.2 world_settings），与剧本一对一。
 */
@Entity(tableName = "world_settings",
        foreignKeys = @ForeignKey(entity = ScriptEntity.class,
                parentColumns = "id",
                childColumns = "script_id",
                onDelete = CASCADE),
        indices = {@Index(value = {"script_id"}, unique = true)})
public class WorldSettingEntity {

    @NonNull
    @PrimaryKey
    public String id;

    public String script_id;

    @Nullable
    public String era;

    @Nullable
    public String location;

    public String factions_json = "[]";

    public String rules_json = "[]";

    @Nullable
    public String story_hook;

    @Nullable
    public String background_full;

    public String tags_json = "[]";

    @Nullable
    public String version_note;

    /** 剧本级扮演要求（用户自由文本，每轮注入提示词），可空。 */
    @Nullable
    public String chat_style_directive;

    /** 每轮最多回复角色数（1..8，默认 2）。 */
    @ColumnInfo(name = "max_responders_per_turn", defaultValue = "2")
    public int max_responders_per_turn = 2;

    public long updated_at;

    public WorldSettingEntity() {
    }

    public WorldSettingEntity(String id, String scriptId, @Nullable String era, @Nullable String location,
                              String factionsJson, String rulesJson, @Nullable String storyHook,
                              @Nullable String backgroundFull, String tagsJson,
                              @Nullable String versionNote, long updatedAt) {
        this(id, scriptId, era, location, factionsJson, rulesJson, storyHook, backgroundFull,
                tagsJson, versionNote, null, 2, updatedAt);
    }

    public WorldSettingEntity(String id, String scriptId, @Nullable String era, @Nullable String location,
                              String factionsJson, String rulesJson, @Nullable String storyHook,
                              @Nullable String backgroundFull, String tagsJson,
                              @Nullable String versionNote, @Nullable String chatStyleDirective,
                              int maxRespondersPerTurn, long updatedAt) {
        this.id = id;
        this.script_id = scriptId;
        this.era = era;
        this.location = location;
        this.factions_json = factionsJson;
        this.rules_json = rulesJson;
        this.story_hook = storyHook;
        this.background_full = backgroundFull;
        this.tags_json = tagsJson;
        this.version_note = versionNote;
        this.chat_style_directive = chatStyleDirective;
        this.max_responders_per_turn = maxRespondersPerTurn;
        this.updated_at = updatedAt;
    }
}
