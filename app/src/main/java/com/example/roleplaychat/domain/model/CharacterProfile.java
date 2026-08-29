package com.example.roleplaychat.domain.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 角色档案领域模型（架构文档 §5.2）。
 */
public final class CharacterProfile {

    private final String id;
    private final String scriptId;
    private final String name;
    private final List<String> aliases;
    @Nullable
    private final String avatarRef;
    @Nullable
    private final String gender;
    @Nullable
    private final String ageText;
    @Nullable
    private final String personality;
    @Nullable
    private final String backstory;
    @Nullable
    private final String speakingStyle;
    private final List<String> catchphrases;
    private final List<String> strengths;
    private final List<String> flaws;
    private final Map<String, String> relationships;
    private final List<String> sampleLines;
    @Nullable
    private final String systemPrompt;
    @Nullable
    private final String hiddenSetting;
    private final boolean enabled;
    private final int sortIndex;
    private final long createdAt;
    private final long updatedAt;
    @Nullable
    private final String extraJson;

    public CharacterProfile(String id, String scriptId, String name, List<String> aliases,
                            @Nullable String avatarRef, @Nullable String gender, @Nullable String ageText,
                            @Nullable String personality, @Nullable String backstory, @Nullable String speakingStyle,
                            List<String> catchphrases, List<String> strengths, List<String> flaws,
                            Map<String, String> relationships, List<String> sampleLines,
                            @Nullable String systemPrompt, @Nullable String hiddenSetting,
                            boolean enabled, int sortIndex, long createdAt, long updatedAt,
                            @Nullable String extraJson) {
        this.id = Objects.requireNonNull(id);
        this.scriptId = Objects.requireNonNull(scriptId);
        this.name = Objects.requireNonNull(name);
        this.aliases = defensiveList(aliases);
        this.avatarRef = avatarRef;
        this.gender = gender;
        this.ageText = ageText;
        this.personality = personality;
        this.backstory = backstory;
        this.speakingStyle = speakingStyle;
        this.catchphrases = defensiveList(catchphrases);
        this.strengths = defensiveList(strengths);
        this.flaws = defensiveList(flaws);
        this.relationships = defensiveMap(relationships);
        this.sampleLines = defensiveList(sampleLines);
        this.systemPrompt = systemPrompt;
        this.hiddenSetting = hiddenSetting;
        this.enabled = enabled;
        this.sortIndex = sortIndex;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.extraJson = extraJson;
    }

    public String getId() {
        return id;
    }

    public String getScriptId() {
        return scriptId;
    }

    public String getName() {
        return name;
    }

    public List<String> getAliases() {
        return Collections.unmodifiableList(aliases);
    }

    @Nullable
    public String getAvatarRef() {
        return avatarRef;
    }

    @Nullable
    public String getGender() {
        return gender;
    }

    @Nullable
    public String getAgeText() {
        return ageText;
    }

    @Nullable
    public String getPersonality() {
        return personality;
    }

    @Nullable
    public String getBackstory() {
        return backstory;
    }

    @Nullable
    public String getSpeakingStyle() {
        return speakingStyle;
    }

    public List<String> getCatchphrases() {
        return Collections.unmodifiableList(catchphrases);
    }

    public List<String> getStrengths() {
        return Collections.unmodifiableList(strengths);
    }

    public List<String> getFlaws() {
        return Collections.unmodifiableList(flaws);
    }

    public Map<String, String> getRelationships() {
        return Collections.unmodifiableMap(relationships);
    }

    public List<String> getSampleLines() {
        return Collections.unmodifiableList(sampleLines);
    }

    @Nullable
    public String getSystemPrompt() {
        return systemPrompt;
    }

    @Nullable
    public String getHiddenSetting() {
        return hiddenSetting;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getSortIndex() {
        return sortIndex;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    @Nullable
    public String getExtraJson() {
        return extraJson;
    }

    private static List<String> defensiveList(List<String> source) {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
    }

    private static Map<String, String> defensiveMap(Map<String, String> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }
}
