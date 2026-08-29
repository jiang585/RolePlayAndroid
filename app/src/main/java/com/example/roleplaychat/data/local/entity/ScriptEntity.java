package com.example.roleplaychat.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 剧本表（架构文档 §6.2 scripts）。
 */
@Entity(tableName = "scripts",
        indices = {@Index(value = {"updated_at"}), @Index(value = {"sort_index"})})
public class ScriptEntity {

    @NonNull
    @PrimaryKey
    public String id;

    public String name;

    @Nullable
    public String one_line;

    @Nullable
    public String cover_ref;

    public long created_at;

    public long updated_at;

    public int sort_index;

    public ScriptEntity() {
    }

    public ScriptEntity(String id, String name, @Nullable String oneLine,
                        @Nullable String coverRef, long createdAt, long updatedAt, int sortIndex) {
        this.id = id;
        this.name = name;
        this.one_line = oneLine;
        this.cover_ref = coverRef;
        this.created_at = createdAt;
        this.updated_at = updatedAt;
        this.sort_index = sortIndex;
    }
}
