package com.example.roleplaychat.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.roleplaychat.data.security.SecretStore;
import com.example.roleplaychat.domain.model.ApiConfig;
import com.example.roleplaychat.domain.repository.SettingsRepository;

/**
 * 设置仓库实现（架构文档 §11）：敏感字段走 SecretStore，普通偏好走 SharedPreferences。
 */
public class SettingsRepositoryImpl implements SettingsRepository {

    private static final String PREFS = "roleplaychat_settings";
    private static final String KEY_BASE_URL = "api_base_url";
    private static final String KEY_MODEL = "api_model";
    private static final String KEY_TEMPERATURE = "api_temperature";
    private static final String KEY_TOP_P = "api_top_p";
    private static final String KEY_MAX_TOKENS = "api_max_tokens";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_CONTEXT_RECENT = "context_recent_count";
    private static final String KEY_PROVIDER = "api_provider";

    private final SharedPreferences prefs;
    private final SecretStore secretStore;
    private final MutableLiveData<ApiConfig> configLiveData = new MutableLiveData<>();
    private ApiConfigChangeListener configChangeListener;

    public SettingsRepositoryImpl(Context context, SecretStore secretStore) {
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.secretStore = secretStore;
        this.configLiveData.setValue(readConfig());
    }

    @Override
    public ApiConfig getApiConfig() {
        return readConfig();
    }

    @Override
    public void saveApiConfig(ApiConfig config) {
        prefs.edit()
                .putString(KEY_PROVIDER, config.getProvider().name())
                .putString(KEY_BASE_URL, config.getBaseUrl())
                .putString(KEY_MODEL, config.getModel())
                .putFloat(KEY_TEMPERATURE, config.getTemperature())
                .putFloat(KEY_TOP_P, config.getTopP())
                .putInt(KEY_MAX_TOKENS, config.getMaxTokens())
                .apply();
        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            secretStore.putSecret(KEY_API_KEY, config.getApiKey());
        }
        configLiveData.setValue(readConfig());
        if (configChangeListener != null) {
            configChangeListener.onChanged(readConfig());
        }
    }

    @Override
    public void setApiConfigChangeListener(ApiConfigChangeListener listener) {
        this.configChangeListener = listener;
    }

    @Override
    public LiveData<ApiConfig> observeApiConfig() {
        return configLiveData;
    }

    @Override
    public int getContextRecentCount() {
        return prefs.getInt(KEY_CONTEXT_RECENT, 40);
    }

    @Override
    public void setContextRecentCount(int count) {
        prefs.edit().putInt(KEY_CONTEXT_RECENT, count).apply();
    }

    private ApiConfig readConfig() {
        String providerValue = prefs.getString(KEY_PROVIDER, ApiConfig.Provider.DEEPSEEK.name());
        ApiConfig.Provider provider;
        try {
            provider = ApiConfig.Provider.valueOf(providerValue);
        } catch (IllegalArgumentException e) {
            provider = ApiConfig.Provider.DEEPSEEK;
        }
        String defaultBaseUrl = defaultBaseUrl(provider);
        String defaultModel = defaultModel(provider);
        String baseUrl = prefs.getString(KEY_BASE_URL, defaultBaseUrl);
        String model = prefs.getString(KEY_MODEL, defaultModel);
        // Migrate the previously shipped DeepSeek preset to the documented endpoint/models.
        if (provider == ApiConfig.Provider.DEEPSEEK) {
            if ("https://api.deepseek.com/v1".equals(baseUrl)) {
                baseUrl = defaultBaseUrl;
            }
            if ("deepseek-chat".equals(model) || "deepseek-reasoner".equals(model)) {
                model = defaultModel;
            }
        }
        float temperature = prefs.getFloat(KEY_TEMPERATURE, 0.8f);
        float topP = prefs.getFloat(KEY_TOP_P, 0.9f);
        int maxTokens = prefs.getInt(KEY_MAX_TOKENS, 2048);
        String apiKey = secretStore.getSecret(KEY_API_KEY);
        return new ApiConfig(provider, baseUrl, apiKey, model, temperature, topP, maxTokens);
    }

    /** 各 provider 的默认 BaseURL（文档：opencode.ai/docs/go）。 */
    private static String defaultBaseUrl(ApiConfig.Provider provider) {
        switch (provider) {
            case DEEPSEEK:
                return "https://api.deepseek.com";
            case OPENCODE_GO:
                return "https://opencode.ai/zen/go/v1";
            case OPENAI_COMPATIBLE:
            default:
                return "https://api.openai.com/v1";
        }
    }

    /** 各 provider 的默认模型。 */
    private static String defaultModel(ApiConfig.Provider provider) {
        switch (provider) {
            case DEEPSEEK:
                return "deepseek-v4-flash";
            case OPENCODE_GO:
                return "deepseek-v4-flash";
            case OPENAI_COMPATIBLE:
            default:
                return "gpt-4o-mini";
        }
    }
}
