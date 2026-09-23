package com.example.roleplaychat.domain.model;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 剧本（群组）领域模型。聚合边界与删除边界（架构文档 §5.1）。
 */
public final class Script {

    public enum MediaMode {
        TEXT_ONLY,
        VISUAL
    }

    private final String id;
    private final String name;
    @Nullable
    private final String oneLine;
    @Nullable
    private final String coverRef;
    private final long createdAt;
    private final long updatedAt;
    private final int sortIndex;
    private final MediaMode mediaMode;

    public Script(String id, String name, @Nullable String oneLine, @Nullable String coverRef,
                  long createdAt, long updatedAt, int sortIndex) {
        this(id, name, oneLine, coverRef, createdAt, updatedAt, sortIndex, MediaMode.TEXT_ONLY);
    }

    public Script(String id, String name, @Nullable String oneLine, @Nullable String coverRef,
                  long createdAt, long updatedAt, int sortIndex, MediaMode mediaMode) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.oneLine = oneLine;
        this.coverRef = coverRef;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.sortIndex = sortIndex;
        this.mediaMode = mediaMode == null ? MediaMode.TEXT_ONLY : mediaMode;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Nullable
    public String getOneLine() {
        return oneLine;
    }

    @Nullable
    public String getCoverRef() {
        return coverRef;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public int getSortIndex() {
        return sortIndex;
    }

    public MediaMode getMediaMode() {
        return mediaMode;
    }

    public boolean isVisual() {
        return mediaMode == MediaMode.VISUAL;
    }

    public Script copyWith(String name, @Nullable String oneLine, @Nullable String coverRef,
                           long updatedAt, int sortIndex) {
        return new Script(id, name, oneLine, coverRef, createdAt, updatedAt, sortIndex, mediaMode);
    }

    public Script copyWithMediaMode(MediaMode mode, long updatedAt) {
        return new Script(id, name, oneLine, coverRef, createdAt, updatedAt, sortIndex, mode);
    }
}
