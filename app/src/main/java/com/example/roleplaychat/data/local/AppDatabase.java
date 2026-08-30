package com.example.roleplaychat.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.example.roleplaychat.data.local.converter.RoomConverters;
import com.example.roleplaychat.data.local.dao.AppearanceDao;
import com.example.roleplaychat.data.local.dao.CharacterDao;
import com.example.roleplaychat.data.local.dao.ImportLogDao;
import com.example.roleplaychat.data.local.dao.MessageDao;
import com.example.roleplaychat.data.local.dao.ScriptDao;
import com.example.roleplaychat.data.local.dao.SessionMemberDao;
import com.example.roleplaychat.data.local.dao.WorldSettingDao;
import com.example.roleplaychat.data.local.entity.AppearanceEntity;
import com.example.roleplaychat.data.local.entity.CharacterEntity;
import com.example.roleplaychat.data.local.entity.ImportLogEntity;
import com.example.roleplaychat.data.local.entity.MessageEntity;
import com.example.roleplaychat.data.local.entity.ScriptEntity;
import com.example.roleplaychat.data.local.entity.SessionMemberEntity;
import com.example.roleplaychat.data.local.entity.WorldSettingEntity;

/**
 * 数据库（架构文档 §6.1）：库名 role_play_chat.db，当前版本 2。
 * 禁止 fallbackToDestructiveMigration；schema 变化必须提供迁移与测试。
 */
@Database(entities = {
        ScriptEntity.class,
        WorldSettingEntity.class,
        CharacterEntity.class,
        SessionMemberEntity.class,
        MessageEntity.class,
        AppearanceEntity.class,
        ImportLogEntity.class
}, version = 2, exportSchema = true)
@TypeConverters(RoomConverters.class)
public abstract class AppDatabase extends RoomDatabase {

    public static final String DATABASE_NAME = "role_play_chat.db";

    public abstract ScriptDao scriptDao();

    public abstract WorldSettingDao worldSettingDao();

    public abstract CharacterDao characterDao();

    public abstract SessionMemberDao sessionMemberDao();

    public abstract MessageDao messageDao();

    public abstract AppearanceDao appearanceDao();

    public abstract ImportLogDao importLogDao();

    public static AppDatabase build(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, DATABASE_NAME)
                .addMigrations(DatabaseMigrations.ALL_MIGRATIONS.toArray(new androidx.room.migration.Migration[0]))
                .build();
    }
}
