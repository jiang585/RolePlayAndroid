package com.example.roleplaychat.domain.model;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 剧本（群组）领域模型。聚合边界与删除边界（架构文档 §5.1）。
 */
public final class Script {

    private final String id;
    private final String name;
    @Nullable
    private final String oneLine;
    @Nullable
    private final String coverRef;
    private final long createdAt;
    private final long updatedAt;
    private final int sortIndex;

    public Script(String id, String name, @Nullable String oneLine, @Nullable String coverRef,
                  long createdAt, long updatedAt, int sortIndex) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.oneLine = oneLine;
        this.coverRef = coverRef;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.sortIndex = sortIndex;
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

    public Script copyWith(String name, @Nullable String oneLine, @Nullable String coverRef,
                           long updatedAt, int sortIndex) {
        return new Script(id, name, oneLine, coverRef, createdAt, updatedAt, sortIndex);
    }
}
