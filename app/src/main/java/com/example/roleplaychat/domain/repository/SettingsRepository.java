package com.example.roleplaychat.domain.repository;

import androidx.lifecycle.LiveData;

import com.example.roleplaychat.domain.model.ApiConfig;

/**
 * 设置仓库接口（架构文档 §11）。
 */
public interface SettingsRepository {

    ApiConfig getApiConfig();

    void saveApiConfig(ApiConfig config);

    void setApiConfigChangeListener(ApiConfigChangeListener listener);

    interface ApiConfigChangeListener {
        void onChanged(ApiConfig config);
    }

    LiveData<ApiConfig> observeApiConfig();

    /** 上下文策略：最近消息条数（默认 40）。 */
    int getContextRecentCount();

    void setContextRecentCount(int count);
}
