package com.example.roleplaychat.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.roleplaychat.data.local.entity.CharacterVisualProfileEntity;

@Dao
public interface CharacterVisualProfileDao {
    @Query("SELECT * FROM character_visual_profiles WHERE character_id = :characterId LIMIT 1")
    CharacterVisualProfileEntity getByCharacterId(String characterId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CharacterVisualProfileEntity entity);

    @Update
    int update(CharacterVisualProfileEntity entity);

    @Query("DELETE FROM character_visual_profiles WHERE character_id = :characterId")
    int deleteByCharacterId(String characterId);
}
