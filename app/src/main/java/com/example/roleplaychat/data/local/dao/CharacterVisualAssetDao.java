package com.example.roleplaychat.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.roleplaychat.data.local.entity.CharacterVisualAssetEntity;

import java.util.List;

@Dao
public interface CharacterVisualAssetDao {
    @Query("SELECT * FROM character_visual_assets WHERE profile_id = :profileId ORDER BY is_primary DESC, created_at ASC")
    List<CharacterVisualAssetEntity> getByProfileId(String profileId);

    @Query("SELECT * FROM character_visual_assets WHERE profile_id = :profileId AND is_primary = 1 LIMIT 1")
    CharacterVisualAssetEntity getPrimary(String profileId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CharacterVisualAssetEntity entity);

    @Query("DELETE FROM character_visual_assets WHERE profile_id = :profileId")
    int deleteByProfileId(String profileId);
}
