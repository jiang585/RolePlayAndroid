package com.example.roleplaychat.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.roleplaychat.data.local.entity.AppearanceEntity;

import java.util.List;

/**
 * 装扮 DAO。
 */
@Dao
public interface AppearanceDao {

    @Query("SELECT * FROM appearances WHERE scope_type = :scopeType AND scope_id = :scopeId LIMIT 1")
    AppearanceEntity getByScope(String scopeType, String scopeId);

    @Query("SELECT * FROM appearances WHERE scope_type = :scopeType AND scope_id = :scopeId LIMIT 1")
    LiveData<AppearanceEntity> observeByScope(String scopeType, String scopeId);

    @Query("SELECT * FROM appearances WHERE scope_type = 'SCRIPT' AND scope_id = :scriptId LIMIT 1")
    LiveData<AppearanceEntity> observeScript(String scriptId);

    @Query("SELECT * FROM appearances WHERE scope_type = 'CHARACTER' AND scope_id = :characterId LIMIT 1")
    LiveData<AppearanceEntity> observeCharacter(String characterId);

    @Query("SELECT * FROM appearances")
    List<AppearanceEntity> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(AppearanceEntity entity);
}
