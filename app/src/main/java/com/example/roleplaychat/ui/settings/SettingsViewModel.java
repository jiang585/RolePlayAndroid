package com.example.roleplaychat.ui.settings;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.roleplaychat.domain.model.ApiConfig;
import com.example.roleplaychat.domain.model.ApiProfile;
import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.AppErrorCode;
import com.example.roleplaychat.domain.repository.AiRepository;
import com.example.roleplaychat.domain.repository.ScriptRepository;
import com.example.roleplaychat.domain.repository.SettingsRepository;
import com.example.roleplaychat.domain.validation.ApiConfigValidator;
import com.example.roleplaychat.ui.common.SingleEvent;
import com.example.roleplaychat.util.AppExecutors;

import java.util.List;
import java.util.UUID;

/**
 * 设置 ViewModel（FR-801~804）。支持多配置档案的新建 / 编辑 / 切换 / 删除。
 */
public class SettingsViewModel extends ViewModel {
    private final SettingsRepository settingsRepository;
    private final AiRepository aiRepository;
    private final ScriptRepository scriptRepository;
    private final AppExecutors executors;

    private final MutableLiveData<SingleEvent<String>> events = new MutableLiveData<>();
    private final MutableLiveData<List<ApiProfile>> profiles = new MutableLiveData<>();
    private final MutableLiveData<ApiProfile> activeProfile = new MutableLiveData<>();
    private final LiveData<ApiConfig> config;

    public SettingsViewModel(SettingsRepository settingsRepository,
                             AiRepository aiRepository,
                             ScriptRepository scriptRepository,
                             AppExecutors executors) {
        this.settingsRepository = settingsRepository;
        this.aiRepository = aiRepository;
        this.scriptRepository = scriptRepository;
        this.executors = executors;
        this.config = settingsRepository.observeApiConfig();
    }

    public LiveData<ApiConfig> getConfig() {
        return config;
    }

    public LiveData<SingleEvent<String>> getEvents() {
        return events;
    }

    public LiveData<List<ApiProfile>> getProfiles() {
        return profiles;
    }

    public LiveData<ApiProfile> getActiveProfile() {
        return activeProfile;
    }

    /** 刷新档案列表与当前启用档案（进入页面与档案操作后调用）。 */
    public void refreshProfiles() {
        profiles.setValue(settingsRepository.getProfiles());
        activeProfile.setValue(settingsRepository.getActiveProfile());
    }

    /** 当前档案快照（供 UI 事件回执时读取）。 */
    public List<ApiProfile> getProfilesValue() {
        return settingsRepository.getProfiles();
    }

    /** 当前启用档案 id（供 UI 渲染单选标记）。 */
    @Nullable
    public String getActiveProfileId() {
        return settingsRepository.getActiveProfileId();
    }

    /**
     * 保存表单为一份配置档案。
     *
     * @param profileId 已有档案 id；null 表示新建
     */
    public void saveProfile(String profileId, String profileName, ApiConfig.Provider provider,
                            String baseUrl, String apiKey, String model,
                            float temperature, float topP, int maxTokens) {
        ApiConfig candidate = new ApiConfig(provider, baseUrl.trim(), apiKey, model.trim(),
                temperature, topP, maxTokens);
        AppError error = ApiConfigValidator.validate(candidate);
        if (error != null) {
            events.setValue(new SingleEvent<>("error:" + error.getMessage()));
            return;
        }
        String id = profileId == null || profileId.trim().isEmpty()
                ? UUID.randomUUID().toString() : profileId;
        try {
            settingsRepository.saveProfile(new ApiProfile(id, profileName, candidate));
        } catch (RuntimeException e) {
            events.setValue(new SingleEvent<>("error:" + e.getMessage()));
            return;
        }
        refreshProfiles();
        events.setValue(new SingleEvent<>("saved:" + id));
    }

    /** 启用某档案（不修改表单内容）。 */
    public void activateProfile(String profileId) {
        executors.diskIO().execute(() -> {
            String error = settingsRepository.setActiveProfile(profileId);
            executors.mainThread().execute(() -> {
                if (error != null) {
                    events.setValue(new SingleEvent<>("error:" + error));
                } else {
                    refreshProfiles();
                    events.setValue(new SingleEvent<>("activated"));
                }
            });
        });
    }

    /** 删除档案（仓库层负责“至少保留一个”守卫与 active 切换）。 */
    public void deleteProfile(String profileId) {
        executors.diskIO().execute(() -> {
            String error = settingsRepository.deleteProfile(profileId);
            executors.mainThread().execute(() -> {
                if (error != null) {
                    events.setValue(new SingleEvent<>("error:" + error));
                } else {
                    refreshProfiles();
                    events.setValue(new SingleEvent<>("profile_deleted"));
                }
            });
        });
    }

    /**
     * 按表单当前内容测试连接：直接测给定配置，不落盘、不篡改当前启用档案。
     */
    public void testConnection(ApiConfig.Provider provider, String baseUrl, String apiKey,
                               String model, float temperature, float topP, int maxTokens) {
        ApiConfig candidate = new ApiConfig(provider, baseUrl.trim(), apiKey, model.trim(),
                temperature, topP, maxTokens);
        executors.networkIO().execute(() -> {
            AppErrorCode code = aiRepository.testConnection(candidate);
            executors.mainThread().execute(() -> {
                if (code == null) {
                    events.setValue(new SingleEvent<>("test_ok"));
                } else {
                    events.setValue(new SingleEvent<>("test_fail:" + code.getCode()));
                }
            });
        });
    }

    public void setContextRecentCount(int count) {
        settingsRepository.setContextRecentCount(count);
    }

    public int getContextRecentCount() {
        return settingsRepository.getContextRecentCount();
    }
}
