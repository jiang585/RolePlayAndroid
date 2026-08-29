package com.example.roleplaychat.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.example.roleplaychat.data.local.AppDatabase;
import com.example.roleplaychat.data.local.dao.WorldSettingDao;
import com.example.roleplaychat.data.local.entity.WorldSettingEntity;
import com.example.roleplaychat.data.mapper.EntityMapper;
import com.example.roleplaychat.domain.model.WorldSetting;
import com.example.roleplaychat.domain.repository.WorldRepository;

/**
 * 世界观仓库实现。
 */
public class WorldRepositoryImpl implements WorldRepository {

    private final AppDatabase db;
    private final WorldSettingDao dao;

    public WorldRepositoryImpl(AppDatabase db) {
        this.db = db;
        this.dao = db.worldSettingDao();
    }

    @Override
    public LiveData<WorldSetting> observeByScriptId(String scriptId) {
        return Transformations.map(dao.observeByScriptId(scriptId), EntityMapper::toWorld);
    }

    @Override
    public WorldSetting getByScriptId(String scriptId) {
        WorldSettingEntity entity = dao.getByScriptId(scriptId);
        return entity == null ? null : EntityMapper.toWorld(entity);
    }

    @Override
    public void save(WorldSetting world) {
        db.runInTransaction(() -> dao.insert(EntityMapper.toEntity(world)));
    }
}
