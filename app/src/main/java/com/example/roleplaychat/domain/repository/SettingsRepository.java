package com.example.roleplaychat.domain.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.example.roleplaychat.domain.model.ApiConfig;
import com.example.roleplaychat.domain.model.ApiProfile;

import java.util.List;

/**
 * 设置仓库接口（架构文档 §11）。支持多配置档案（Profile）。
 */
public interface SettingsRepository {

    ApiConfig getApiConfig();

    void saveApiConfig(ApiConfig config);

    void setApiConfigChangeListener(ApiConfigChangeListener listener);

    interface ApiConfigChangeListener {
        void onChanged(ApiConfig config);
    }

    LiveData<ApiConfig> observeApiConfig();

    // ---------- 多配置档案 ----------

    /** 全部配置档案（按保存顺序）。 */
    List<ApiProfile> getProfiles();

    /** 当前启用档案；永不为 null（无档案时返回内置默认档案）。 */
    ApiProfile getActiveProfile();

    /** 当前启用档案 id；无档案时为 null。 */
    @Nullable
    String getActiveProfileId();

    /** 新建或更新档案（含密钥写入 SecretStore，别名按档案隔离）。 */
    void saveProfile(ApiProfile profile);

    /**
     * 删除档案。删除当前启用档案时自动切换到第一个；只剩一个档案时拒绝删除。
     *
     * @return 错误提示；null 表示成功
     */
    @Nullable
    String deleteProfile(String profileId);

    /** 启用档案：触发 ApiConfigChangeListener 以更新运行时服务。 */
    @Nullable
    String setActiveProfile(String profileId);

    /** 上下文策略：最近消息条数（默认 40）。 */
    int getContextRecentCount();

    void setContextRecentCount(int count);
}
