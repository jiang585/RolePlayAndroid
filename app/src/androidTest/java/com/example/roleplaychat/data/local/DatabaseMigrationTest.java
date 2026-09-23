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
 * 迁移测试：旧数据字段保持不变；v3 只新增朋友圈表，不重建消息表。
 */
@RunWith(AndroidJUnit4.class)
public class DatabaseMigrationTest {

    private static final String DB_NAME = "migration-test.db";
    private static final String DB_NAME_V3 = "migration-v3-test.db";
    private static final String DB_NAME_V4 = "migration-v4-test.db";
    private static final String DB_NAME_V5 = "migration-v5-test.db";

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

    @Test
    public void migrate2To3_addsMomentsWithoutChangingExistingMessages() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(DB_NAME_V3, 2);
        ContentValues script = new ContentValues();
        script.put("id", "s1"); script.put("name", "旧剧本");
        script.put("created_at", 1L); script.put("updated_at", 1L); script.put("sort_index", 0);
        db.insert("scripts", SQLiteDatabase.CONFLICT_REPLACE, script);
        ContentValues message = new ContentValues();
        message.put("id", "m1"); message.put("script_id", "s1"); message.put("type", "CHARACTER_TEXT");
        message.put("side", "MINE"); message.put("content", "这是一条升级前的聊天记录");
        message.put("sequence", 1L); message.put("created_at", 1L); message.put("status", "DONE");
        db.insert("messages", SQLiteDatabase.CONFLICT_REPLACE, message);
        db.close();

        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(DB_NAME_V3, 3, true,
                DatabaseMigrations.MIGRATION_2_3);
        Cursor messageCursor = migrated.query("SELECT content FROM messages WHERE id = 'm1'");
        assertEquals(1, messageCursor.getCount()); messageCursor.moveToFirst();
        assertEquals("这是一条升级前的聊天记录", messageCursor.getString(0)); messageCursor.close();
        Cursor momentsCursor = migrated.query("SELECT name FROM sqlite_master WHERE type='table' AND name='moments'");
        assertEquals(1, momentsCursor.getCount()); momentsCursor.close();
    }

    @Test
    public void migrate3To4_preservesCommentsAndAddsReplyReference() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(DB_NAME_V4, 3);
        ContentValues script = new ContentValues();
        script.put("id", "s1"); script.put("name", "剧本"); script.put("created_at", 1L); script.put("updated_at", 1L); script.put("sort_index", 0);
        db.insert("scripts", SQLiteDatabase.CONFLICT_REPLACE, script);
        ContentValues moment = new ContentValues();
        moment.put("id", "moment1"); moment.put("script_id", "s1"); moment.put("author_type", "PLAYER"); moment.put("content", "旧动态"); moment.put("created_at", 1L); moment.put("source_sequence", 0L);
        db.insert("moments", SQLiteDatabase.CONFLICT_REPLACE, moment);
        ContentValues comment = new ContentValues();
        comment.put("id", "comment1"); comment.put("moment_id", "moment1"); comment.put("author_type", "PLAYER"); comment.put("content", "旧评论"); comment.put("created_at", 2L);
        db.insert("moment_comments", SQLiteDatabase.CONFLICT_REPLACE, comment);
        db.close();

        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(DB_NAME_V4, 4, true,
                DatabaseMigrations.MIGRATION_3_4);
        Cursor cursor = migrated.query("SELECT content, parent_comment_id FROM moment_comments WHERE id = 'comment1'");
        assertEquals(1, cursor.getCount()); cursor.moveToFirst();
        assertEquals("旧评论", cursor.getString(0)); assertNull(cursor.getString(1)); cursor.close();
    }

    @Test
    public void migrate4To5_upgradesOldScriptToTextOnlyAndAddsVisualTables() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(DB_NAME_V5, 4);
        ContentValues script = new ContentValues();
        script.put("id", "s1"); script.put("name", "旧剧本");
        script.put("created_at", 1L); script.put("updated_at", 1L); script.put("sort_index", 0);
        db.insert("scripts", SQLiteDatabase.CONFLICT_REPLACE, script);
        db.close();

        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(DB_NAME_V5, 5, true,
                DatabaseMigrations.MIGRATION_4_5);
        Cursor scriptCursor = migrated.query("SELECT media_mode FROM scripts WHERE id = 's1'");
        assertEquals(1, scriptCursor.getCount()); scriptCursor.moveToFirst();
        assertEquals("TEXT_ONLY", scriptCursor.getString(0)); scriptCursor.close();
        Cursor tables = migrated.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN "
                + "('character_visual_profiles','character_visual_assets','image_generation_jobs','message_attachments')");
        assertEquals(4, tables.getCount()); tables.close();
    }
}
