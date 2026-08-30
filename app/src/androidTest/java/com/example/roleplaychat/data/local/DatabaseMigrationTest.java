package com.example.roleplaychat.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * 迁移测试（v1 → v2）：world_settings 新增 chat_style_directive（NULL）
 * 与 max_responders_per_turn（NOT NULL DEFAULT 2）；旧数据保持不变。
 */
@RunWith(AndroidJUnit4.class)
public class DatabaseMigrationTest {

    private static final String DB_NAME = "migration-test.db";

    @Rule
    public final MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(), AppDatabase.class);

    @Test
    public void migrate1To2_newColumnsHaveDefaultsAndOldDataSurvives() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(DB_NAME, 1);
        ContentValues world = new ContentValues();
        world.put("id", "w1");
        world.put("script_id", "s1");
        world.put("era", "现代");
        world.put("location", "临江市");
        world.put("factions_json", "[]");
        world.put("rules_json", "[]");
        world.putNull("story_hook");
        world.putNull("background_full");
        world.put("tags_json", "[]");
        world.putNull("version_note");
        world.put("updated_at", 1L);
        db.insert("world_settings", SQLiteDatabase.CONFLICT_REPLACE, world);
        db.close();

        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(DB_NAME, 2, true,
                DatabaseMigrations.MIGRATION_1_2);

        Cursor cursor = migrated.query(
                "SELECT era, chat_style_directive, max_responders_per_turn "
                        + "FROM world_settings WHERE id = 'w1'");
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("现代", cursor.getString(0));
        assertNull(cursor.getString(1));
        assertEquals(2, cursor.getInt(2));
        cursor.close();
    }
}
