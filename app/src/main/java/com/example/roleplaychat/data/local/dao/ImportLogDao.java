package com.example.roleplaychat.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.roleplaychat.data.local.entity.ImportLogEntity;

import java.util.List;

/**
 * 导入日志 DAO。
 */
@Dao
public interface ImportLogDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(ImportLogEntity entity);

    @Query("SELECT * FROM import_logs ORDER BY imported_at DESC LIMIT 50")
    List<ImportLogEntity> getRecent();

    @Query("DELETE FROM import_logs")
    int clear();
}
