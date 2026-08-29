package com.example.roleplaychat.domain.repository;

import androidx.lifecycle.LiveData;

import com.example.roleplaychat.domain.model.WorldSetting;

/**
 * 世界观仓库接口。
 */
public interface WorldRepository {

    LiveData<WorldSetting> observeByScriptId(String scriptId);

    WorldSetting getByScriptId(String scriptId);

    void save(WorldSetting world);
}
