package com.example.roleplaychat.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.roleplaychat.data.local.entity.AppearanceEntity;
import com.example.roleplaychat.data.local.entity.ScriptEntity;
import com.example.roleplaychat.data.local.entity.SessionMemberEntity;
import com.example.roleplaychat.data.local.entity.WorldSettingEntity;

import java.util.List;

/**
 * 剧本 DAO（架构文档 §6.2）。
 */
@Dao
public interface ScriptDao {

    @Query("SELECT * FROM scripts ORDER BY updated_at DESC")
    LiveData<List<ScriptEntity>> observeAll();

    @Query("SELECT * FROM scripts ORDER BY updated_at DESC")
    List<ScriptEntity> getAll();

    @Query("SELECT * FROM scripts WHERE id = :id")
    LiveData<ScriptEntity> observeById(String id);

    @Query("SELECT * FROM scripts WHERE id = :id")
    ScriptEntity getById(String id);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(ScriptEntity entity);

    @Update
    int update(ScriptEntity entity);

    @Delete
    int delete(ScriptEntity entity);

    @Query("DELETE FROM scripts WHERE id = :id")
    int deleteById(String id);

    @Query("UPDATE scripts SET updated_at = :now WHERE id = :id")
    int touchUpdatedAt(String id, long now);

    @Query("SELECT COUNT(*) FROM scripts")
    int count();

    /** 创建剧本事务（§7.2：Script + World + SCRIPT Appearance + Player slot）。 */
    @Transaction
    default void createScriptBundle(ScriptEntity script, WorldSettingEntity world,
                                    AppearanceEntity appearance, SessionMemberEntity playerSlot) {
        insert(script);
        worldInsert(world);
        appearanceInsert(appearance);
        memberInsert(playerSlot);
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void worldInsert(WorldSettingEntity world);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void appearanceInsert(AppearanceEntity appearance);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void memberInsert(SessionMemberEntity member);
}
