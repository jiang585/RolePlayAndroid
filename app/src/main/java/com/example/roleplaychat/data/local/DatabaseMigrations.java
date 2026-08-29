package com.example.roleplaychat.data.local;

import androidx.room.migration.Migration;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据库迁移注册表（架构文档 §6.1）。
 * 初始版本 1；任何 schema 变化必须在此追加迁移并补充迁移测试。
 */
public final class DatabaseMigrations {

    /** 当前数据库版本。 */
    public static final int CURRENT_VERSION = 1;

    /** 全部迁移列表（按版本升序）。 */
    public static final List<Migration> ALL_MIGRATIONS = new ArrayList<>();

    static {
        // 示例：从版本 1 到 2 的迁移在此追加。
        // ALL_MIGRATIONS.add(new Migration(1, 2) {
        //     @Override
        //     public void migrate(@NonNull SupportSQLiteDatabase database) {
        //         database.execSQL("ALTER TABLE scripts ADD COLUMN ...");
        //     }
        // });
    }

    private DatabaseMigrations() {
    }
}
