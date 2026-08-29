package com.example.roleplaychat.domain.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 世界观领域模型（架构文档 §6.2 world_settings，与剧本一对一）。
 */
public final class WorldSetting {

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
    private final long updatedAt;

    public WorldSetting(String id, String scriptId, @Nullable String era, @Nullable String location,
                        List<String> factions, List<String> rules, @Nullable String storyHook,
                        @Nullable String backgroundFull, List<String> tags,
                        @Nullable String versionNote, long updatedAt) {
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

    public long getUpdatedAt() {
        return updatedAt;
    }

    private static List<String> defensive(List<String> source) {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
    }
}
