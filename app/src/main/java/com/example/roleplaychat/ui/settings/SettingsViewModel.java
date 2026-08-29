package com.example.roleplaychat.ui.settings;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.roleplaychat.domain.model.ApiConfig;
import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.AppErrorCode;
import com.example.roleplaychat.domain.repository.AiRepository;
import com.example.roleplaychat.domain.repository.ScriptRepository;
import com.example.roleplaychat.domain.repository.SettingsRepository;
import com.example.roleplaychat.domain.validation.ApiConfigValidator;
import com.example.roleplaychat.ui.common.SingleEvent;
import com.example.roleplaychat.util.AppExecutors;

/**
 * 设置 ViewModel（FR-801~804）。
 */
public class SettingsViewModel extends ViewModel {

    private final SettingsRepository settingsRepository;
    private final AiRepository aiRepository;
    private final ScriptRepository scriptRepository;
    private final AppExecutors executors;

    private final MutableLiveData<SingleEvent<String>> events = new MutableLiveData<>();
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

    public void save(ApiConfig.Provider provider, String baseUrl, String apiKey, String model,
                     float temperature, float topP, int maxTokens) {
        ApiConfig candidate = new ApiConfig(provider, baseUrl.trim(), apiKey, model.trim(),
                temperature, topP, maxTokens);
        AppError error = ApiConfigValidator.validate(candidate);
        if (error != null) {
            events.setValue(new SingleEvent<>("error:" + error.getMessage()));
            return;
        }
        settingsRepository.saveApiConfig(candidate);
        events.setValue(new SingleEvent<>("saved"));
    }

    public void testConnection() {
        executors.networkIO().execute(() -> {
            AppErrorCode code = aiRepository.testConnection();
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
