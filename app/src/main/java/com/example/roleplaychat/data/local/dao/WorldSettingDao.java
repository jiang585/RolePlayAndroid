package com.example.roleplaychat.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.roleplaychat.data.local.entity.WorldSettingEntity;

/**
 * 世界观 DAO。
 */
@Dao
public interface WorldSettingDao {

    @Query("SELECT * FROM world_settings WHERE script_id = :scriptId LIMIT 1")
    LiveData<WorldSettingEntity> observeByScriptId(String scriptId);

    @Query("SELECT * FROM world_settings WHERE script_id = :scriptId LIMIT 1")
    WorldSettingEntity getByScriptId(String scriptId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(WorldSettingEntity entity);

    @Update
    int update(WorldSettingEntity entity);

    @Query("UPDATE world_settings SET era = :era, location = :location, factions_json = :factionsJson, " +
            "rules_json = :rulesJson, story_hook = :storyHook, background_full = :backgroundFull, " +
            "tags_json = :tagsJson, version_note = :versionNote, updated_at = :updatedAt " +
            "WHERE script_id = :scriptId")
    int updateByScriptId(String scriptId, String era, String location, String factionsJson,
                         String rulesJson, String storyHook, String backgroundFull,
                         String tagsJson, String versionNote, long updatedAt);
}
