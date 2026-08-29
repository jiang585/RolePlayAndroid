package com.example.roleplaychat.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;

/**
 * 角色表（架构文档 §6.2 characters）。
 */
@Entity(tableName = "characters",
        foreignKeys = @ForeignKey(entity = ScriptEntity.class,
                parentColumns = "id",
                childColumns = "script_id",
                onDelete = CASCADE),
        indices = {
                @Index(value = {"script_id", "sort_index"}),
                @Index(value = {"script_id", "enabled"}),
                @Index(value = {"script_id", "name"})
        })
public class CharacterEntity {

    @NonNull
    @PrimaryKey
    public String id;

    public String script_id;

    public String name;

    public String aliases_json = "[]";

    @Nullable
    public String avatar_ref;

    @Nullable
    public String gender;

    @Nullable
    public String age_text;

    @Nullable
    public String personality;

    @Nullable
    public String backstory;

    @Nullable
    public String speaking_style;

    public String catchphrases_json = "[]";

    public String strengths_json = "[]";

    public String flaws_json = "[]";

    public String relationships_json = "{}";

    public String sample_lines_json = "[]";

    @Nullable
    public String system_prompt;

    @Nullable
    public String hidden_setting;

    public boolean enabled;

    public int sort_index;

    public long created_at;

    public long updated_at;

    @Nullable
    public String extra_json;

    public CharacterEntity() {
    }

    public CharacterEntity(String id, String scriptId, String name, String aliasesJson,
                           @Nullable String avatarRef, @Nullable String gender, @Nullable String ageText,
                           @Nullable String personality, @Nullable String backstory, @Nullable String speakingStyle,
                           String catchphrasesJson, String strengthsJson, String flawsJson,
                           String relationshipsJson, String sampleLinesJson,
                           @Nullable String systemPrompt, @Nullable String hiddenSetting,
                           boolean enabled, int sortIndex, long createdAt, long updatedAt,
                           @Nullable String extraJson) {
        this.id = id;
        this.script_id = scriptId;
        this.name = name;
        this.aliases_json = aliasesJson;
        this.avatar_ref = avatarRef;
        this.gender = gender;
        this.age_text = ageText;
        this.personality = personality;
        this.backstory = backstory;
        this.speaking_style = speakingStyle;
        this.catchphrases_json = catchphrasesJson;
        this.strengths_json = strengthsJson;
        this.flaws_json = flawsJson;
        this.relationships_json = relationshipsJson;
        this.sample_lines_json = sampleLinesJson;
        this.system_prompt = systemPrompt;
        this.hidden_setting = hiddenSetting;
        this.enabled = enabled;
        this.sort_index = sortIndex;
        this.created_at = createdAt;
        this.updated_at = updatedAt;
        this.extra_json = extraJson;
    }
}
