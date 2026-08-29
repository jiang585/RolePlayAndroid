package com.example.roleplaychat.data.mapper;

import androidx.annotation.Nullable;

import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.util.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色卡 <-> 领域模型映射（架构文档 §9.2/§9.3）。
 * 默认导出不含 hidden_setting（架构文档 §9.2）。
 */
public final class CharacterCardMapper {

    public static final String FORMAT = "roleplay-character-card";
    public static final int SCHEMA_VERSION = 1;

    private CharacterCardMapper() {
    }

    /** 将领域模型转为导出信封（不含隐藏设定，除非显式包含）。 */
    public static CharacterCardMapper.Envelope toEnvelope(CharacterProfile profile, boolean includeHidden) {
        CharacterCardMapper.Envelope envelope = new CharacterCardMapper.Envelope();
        envelope.format = FORMAT;
        envelope.schemaVersion = SCHEMA_VERSION;
        envelope.generator = "RolePlayChat/1.0";

        CharacterCardMapper.Data data = new CharacterCardMapper.Data();
        data.externalId = profile.getId();
        data.name = profile.getName();
        data.aliases = new ArrayList<>(profile.getAliases());
        data.gender = profile.getGender();
        data.age = profile.getAgeText();
        data.personality = profile.getPersonality();
        data.backstory = profile.getBackstory();
        data.speakingStyle = profile.getSpeakingStyle();
        data.catchphrases = new ArrayList<>(profile.getCatchphrases());
        data.strengths = new ArrayList<>(profile.getStrengths());
        data.flaws = new ArrayList<>(profile.getFlaws());
        for (Map.Entry<String, String> entry : profile.getRelationships().entrySet()) {
            CharacterCardMapper.Relationship rel = new CharacterCardMapper.Relationship();
            rel.targetName = entry.getKey();
            rel.relation = entry.getValue();
            data.relationships.add(rel);
        }
        data.sampleLines = new ArrayList<>(profile.getSampleLines());
        data.systemPrompt = profile.getSystemPrompt();
        if (includeHidden) {
            data.hiddenSetting = profile.getHiddenSetting();
        }
        data.extensions = parseExtensions(profile.getExtraJson());
        envelope.data = data;
        return envelope;
    }

    /** 从信封数据构建领域模型（不包含 id/scriptId，由调用方指定）。 */
    public static CharacterProfile toProfile(CharacterCardMapper.Data data, String characterId, String scriptId,
                                             boolean enabled, int sortIndex, long now) {
        Map<String, String> relationships = new LinkedHashMap<>();
        if (data.relationships != null) {
            for (CharacterCardMapper.Relationship rel : data.relationships) {
                if (rel != null && rel.targetName != null && !rel.targetName.isEmpty()) {
                    relationships.put(rel.targetName, rel.relation == null ? "" : rel.relation);
                }
            }
        }
        return new CharacterProfile(
                characterId,
                scriptId,
                data.name == null || data.name.isEmpty() ? "未命名角色" : data.name,
                data.aliases == null ? new ArrayList<>() : data.aliases,
                null, // avatarRef 由调用方导入后回填
                data.gender,
                data.age,
                data.personality,
                data.backstory,
                data.speakingStyle,
                data.catchphrases == null ? new ArrayList<>() : data.catchphrases,
                data.strengths == null ? new ArrayList<>() : data.strengths,
                data.flaws == null ? new ArrayList<>() : data.flaws,
                relationships,
                data.sampleLines == null ? new ArrayList<>() : data.sampleLines,
                data.systemPrompt,
                data.hiddenSetting,
                enabled,
                sortIndex,
                now,
                now,
                JsonUtils.toJson(data.extensions == null ? new LinkedHashMap<>() : data.extensions));
    }

    private static Map<String, Object> parseExtensions(@Nullable String extraJson) {
        if (extraJson == null || extraJson.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = JsonUtils.fromJson(extraJson,
                    new com.google.gson.reflect.TypeToken<Map<String, Object>>() {
                    }.getType());
            return map == null ? new LinkedHashMap<>() : map;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /** 信封（架构文档 §9.1）。 */
    public static final class Envelope {
        @com.google.gson.annotations.SerializedName("format")
        public String format;
        @com.google.gson.annotations.SerializedName("schema_version")
        public int schemaVersion;
        @com.google.gson.annotations.SerializedName("exported_at")
        public String exportedAt;
        @com.google.gson.annotations.SerializedName("generator")
        public String generator;
        @com.google.gson.annotations.SerializedName("data")
        public Data data;
    }

    /** 角色卡数据部分（§9.2）。 */
    public static final class Data {
        @com.google.gson.annotations.SerializedName("external_id")
        public String externalId;
        @com.google.gson.annotations.SerializedName("name")
        public String name;
        @com.google.gson.annotations.SerializedName("aliases")
        public List<String> aliases = new ArrayList<>();
        @com.google.gson.annotations.SerializedName("gender")
        public String gender;
        @com.google.gson.annotations.SerializedName("age")
        public String age;
        @com.google.gson.annotations.SerializedName("personality")
        public String personality;
        @com.google.gson.annotations.SerializedName("backstory")
        public String backstory;
        @com.google.gson.annotations.SerializedName("speaking_style")
        public String speakingStyle;
        @com.google.gson.annotations.SerializedName("catchphrases")
        public List<String> catchphrases = new ArrayList<>();
        @com.google.gson.annotations.SerializedName("strengths")
        public List<String> strengths = new ArrayList<>();
        @com.google.gson.annotations.SerializedName("flaws")
        public List<String> flaws = new ArrayList<>();
        @com.google.gson.annotations.SerializedName("relationships")
        public List<Relationship> relationships = new ArrayList<>();
        @com.google.gson.annotations.SerializedName("sample_lines")
        public List<String> sampleLines = new ArrayList<>();
        @com.google.gson.annotations.SerializedName("system_prompt")
        public String systemPrompt;
        @com.google.gson.annotations.SerializedName("hidden_setting")
        public String hiddenSetting;
        @com.google.gson.annotations.SerializedName("avatar")
        public Avatar avatar;
        @com.google.gson.annotations.SerializedName("appearance")
        public Appearance appearance;
        @com.google.gson.annotations.SerializedName("extensions")
        public Map<String, Object> extensions = new LinkedHashMap<>();
    }

    public static final class Relationship {
        @com.google.gson.annotations.SerializedName("target_name")
        public String targetName;
        @com.google.gson.annotations.SerializedName("relation")
        public String relation;
    }

    public static final class Avatar {
        @com.google.gson.annotations.SerializedName("mode")
        public String mode; // embedded | file
        @com.google.gson.annotations.SerializedName("media_type")
        public String mediaType;
        @com.google.gson.annotations.SerializedName("data_base64")
        public String dataBase64;
        @com.google.gson.annotations.SerializedName("file_name")
        public String fileName;
    }

    public static final class Appearance {
        @com.google.gson.annotations.SerializedName("bubble_style_id")
        public String bubbleStyleId;
        @com.google.gson.annotations.SerializedName("bubble_color")
        public String bubbleColor;
        @com.google.gson.annotations.SerializedName("text_color")
        public String textColor;
        @com.google.gson.annotations.SerializedName("nickname_color")
        public String nicknameColor;
    }
}
