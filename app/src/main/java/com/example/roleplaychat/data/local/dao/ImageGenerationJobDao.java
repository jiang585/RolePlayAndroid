package com.example.roleplaychat.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.roleplaychat.data.local.entity.ImageGenerationJobEntity;

import java.util.List;

@Dao
public interface ImageGenerationJobDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(ImageGenerationJobEntity entity);

    @Update
    int update(ImageGenerationJobEntity entity);

    @Query("SELECT * FROM image_generation_jobs WHERE id = :id LIMIT 1")
    ImageGenerationJobEntity getById(String id);

    @Query("SELECT * FROM image_generation_jobs WHERE client_job_id = :clientJobId LIMIT 1")
    ImageGenerationJobEntity getByClientJobId(String clientJobId);

    @Query("SELECT * FROM image_generation_jobs WHERE script_id = :scriptId ORDER BY created_at DESC")
    List<ImageGenerationJobEntity> getByScriptId(String scriptId);

    @Query("SELECT * FROM image_generation_jobs WHERE status IN ('CREATED','UPLOADING_REFERENCES','QUEUED','RUNNING','DOWNLOADING')")
    List<ImageGenerationJobEntity> getPending();

    @Query("SELECT COUNT(*) FROM image_generation_jobs WHERE script_id = :scriptId AND character_id = :characterId "
            + "AND trigger = 'SPONTANEOUS_CHARACTER_SHARE' AND created_at >= :since")
    int countRecentSpontaneous(String scriptId, String characterId, long since);
}
