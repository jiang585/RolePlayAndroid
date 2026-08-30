package com.example.roleplaychat.domain.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 世界观领域模型（架构文档 §6.2 world_settings，与剧本一对一）。
 * 含剧本级对话规则：扮演要求（chatStyleDirective）与每轮最多回复角色数
 * （maxRespondersPerTurn，1..8，默认 2）。
 */
public final class WorldSetting {

    public static final int DEFAULT_MAX_RESPONDERS = 2;
    public static final int MIN_MAX_RESPONDERS = 1;
    public static final int MAX_MAX_RESPONDERS = 8;

    private final String id;
    private final String scriptId;
    @Nullable
    private final String era;
    @Nullable
    private final String location;
    private final List<String> factions;
    private final List<String> rules;
    @Nullable
    private final String storyHook;
    @Nullable
    private final String backgroundFull;
    private final List<String> tags;
    @Nullable
    private final String versionNote;
    @Nullable
    private final String chatStyleDirective;
    private final int maxRespondersPerTurn;
    private final long updatedAt;

    public WorldSetting(String id, String scriptId, @Nullable String era, @Nullable String location,
                        List<String> factions, List<String> rules, @Nullable String storyHook,
                        @Nullable String backgroundFull, List<String> tags,
                        @Nullable String versionNote, long updatedAt) {
        this(id, scriptId, era, location, factions, rules, storyHook, backgroundFull, tags,
                versionNote, null, DEFAULT_MAX_RESPONDERS, updatedAt);
    }

    public WorldSetting(String id, String scriptId, @Nullable String era, @Nullable String location,
                        List<String> factions, List<String> rules, @Nullable String storyHook,
                        @Nullable String backgroundFull, List<String> tags,
                        @Nullable String versionNote, @Nullable String chatStyleDirective,
                        int maxRespondersPerTurn, long updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.scriptId = Objects.requireNonNull(scriptId);
        this.era = era;
        this.location = location;
        this.factions = defensive(factions);
        this.rules = defensive(rules);
        this.storyHook = storyHook;
        this.backgroundFull = backgroundFull;
        this.tags = defensive(tags);
        this.versionNote = versionNote;
        this.chatStyleDirective = chatStyleDirective == null || chatStyleDirective.trim().isEmpty()
                ? null : chatStyleDirective.trim();
        this.maxRespondersPerTurn = clampResponders(maxRespondersPerTurn);
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getScriptId() {
        return scriptId;
    }

    @Nullable
    public String getEra() {
        return era;
    }

    @Nullable
    public String getLocation() {
        return location;
    }

    public List<String> getFactions() {
        return Collections.unmodifiableList(factions);
    }

    public List<String> getRules() {
        return Collections.unmodifiableList(rules);
    }

    @Nullable
    public String getStoryHook() {
        return storyHook;
    }

    @Nullable
    public String getBackgroundFull() {
        return backgroundFull;
    }

    public List<String> getTags() {
        return Collections.unmodifiableList(tags);
    }

    @Nullable
    public String getVersionNote() {
        return versionNote;
    }

    /** 剧本级扮演要求；空表示未设置。 */
    @Nullable
    public String getChatStyleDirective() {
        return chatStyleDirective;
    }

    public int getMaxRespondersPerTurn() {
        return maxRespondersPerTurn;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    private static int clampResponders(int value) {
        if (value < MIN_MAX_RESPONDERS) {
            return DEFAULT_MAX_RESPONDERS;
        }
        return Math.min(value, MAX_MAX_RESPONDERS);
    }

    private static List<String> defensive(List<String> source) {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
    }
}
