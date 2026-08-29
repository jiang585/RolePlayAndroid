package com.example.roleplaychat.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.roleplaychat.data.local.entity.CharacterEntity;

import java.util.List;

/**
 * 角色 DAO。
 */
@Dao
public interface CharacterDao {

    @Query("SELECT * FROM characters WHERE script_id = :scriptId ORDER BY sort_index ASC, created_at ASC")
    LiveData<List<CharacterEntity>> observeByScriptId(String scriptId);

    @Query("SELECT * FROM characters WHERE script_id = :scriptId ORDER BY sort_index ASC, created_at ASC")
    List<CharacterEntity> getAllByScriptId(String scriptId);

    @Query("SELECT * FROM characters WHERE script_id = :scriptId AND enabled = 1 ORDER BY sort_index ASC, created_at ASC")
    List<CharacterEntity> getEnabledByScriptId(String scriptId);

    @Query("SELECT * FROM characters WHERE id = :id LIMIT 1")
    CharacterEntity getById(String id);

    @Query("SELECT * FROM characters WHERE id IN (:ids)")
    List<CharacterEntity> getByIds(List<String> ids);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(CharacterEntity entity);

    @Update
    int update(CharacterEntity entity);

    @Query("UPDATE characters SET enabled = :enabled, updated_at = :now WHERE id = :id")
    int setEnabled(String id, boolean enabled, long now);

    @Query("DELETE FROM characters WHERE id = :id")
    int deleteById(String id);

    @Query("SELECT COALESCE(MAX(sort_index), -1) + 1 FROM characters WHERE script_id = :scriptId")
    int nextSortIndex(String scriptId);

    @Query("SELECT COUNT(*) FROM characters WHERE script_id = :scriptId AND enabled = 1")
    int countEnabled(String scriptId);
}
