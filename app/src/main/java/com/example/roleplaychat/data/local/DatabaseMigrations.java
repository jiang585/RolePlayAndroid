package com.example.roleplaychat.data.local;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据库迁移注册表（架构文档 §6.1）。
 * 任何 schema 变化必须在此追加迁移并补充迁移测试。
 */
public final class DatabaseMigrations {

    /** 当前数据库版本。 */
    public static final int CURRENT_VERSION = 2;

    /** v1 -> v2：world_settings 新增剧本级对话规则（扮演要求 + 每轮回复上限）。 */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE world_settings ADD COLUMN chat_style_directive TEXT");
            db.execSQL("ALTER TABLE world_settings ADD COLUMN max_responders_per_turn "
                    + "INTEGER NOT NULL DEFAULT 2");
        }
    };

    /** 全部迁移列表（按版本升序）。 */
    public static final List<Migration> ALL_MIGRATIONS = new ArrayList<>();

    static {
        ALL_MIGRATIONS.add(MIGRATION_1_2);
    }

    private DatabaseMigrations() {
    }
}
