package com.example.roleplaychat.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.roleplaychat.data.security.SecretStore;
import com.example.roleplaychat.domain.model.ApiConfig;
import com.example.roleplaychat.domain.model.ApiProfile;
import com.example.roleplaychat.domain.repository.SettingsRepository;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 设置仓库实现（架构文档 §11）：敏感字段走 SecretStore，普通偏好走 SharedPreferences。
 * 支持多配置档案：档案元数据存 {@code api_profiles_json}（不含密钥），
 * 密钥按 {@code api_key_<profileId>} 别名隔离存 SecretStore，
 * 当前启用档案存 {@code active_profile_id}。
 * 旧的单配置在首次访问时自动迁移为 id=legacy 的档案（旧键保留，回滚安全）。
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

    private static final String KEY_PROFILES_JSON = "api_profiles_json";
    private static final String KEY_ACTIVE_PROFILE = "active_profile_id";
    private static final String KEY_PROFILES_MIGRATED = "api_profiles_migrated";
    private static final String KEY_ALIAS_PREFIX = "api_key_";

    private final SharedPreferences prefs;
    private final SecretStore secretStore;
    private final MutableLiveData<ApiConfig> configLiveData = new MutableLiveData<>();
    private ApiConfigChangeListener configChangeListener;
    private final Object profileLock = new Object();

    public SettingsRepositoryImpl(Context context, SecretStore secretStore) {
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.secretStore = secretStore;
        migrateLegacyConfigIfNeeded();
        this.configLiveData.setValue(readActiveConfig());
    }

    @Override
    public ApiConfig getApiConfig() {
        return readActiveConfig();
    }

    @Override
    public void saveApiConfig(ApiConfig config) {
        // 兼容旧入口：写入当前启用档案。
        ApiProfile active = getActiveProfile();
        saveProfile(new ApiProfile(active.getId(), active.getName(), config));
    }

    @Override
    public void setApiConfigChangeListener(ApiConfigChangeListener listener) {
        this.configChangeListener = listener;
    }

    @Override
    public LiveData<ApiConfig> observeApiConfig() {
        return configLiveData;
    }

    // ---------- 多配置档案 ----------

    @Override
    public List<ApiProfile> getProfiles() {
        synchronized (profileLock) {
            return readProfiles();
        }
    }

    @Override
    public ApiProfile getActiveProfile() {
        synchronized (profileLock) {
            List<ApiProfile> profiles = readProfiles();
            String activeId = prefs.getString(KEY_ACTIVE_PROFILE, null);
            for (ApiProfile profile : profiles) {
                if (profile.getId().equals(activeId)) {
                    return profile;
                }
            }
            return profiles.isEmpty() ? builtinDefaultProfile() : profiles.get(0);
        }
    }

    @Nullable
    @Override
    public String getActiveProfileId() {
        return prefs.getString(KEY_ACTIVE_PROFILE, null);
    }

    @Override
    public void saveProfile(ApiProfile profile) {
        if (profile == null) {
            return;
        }
        synchronized (profileLock) {
            List<ApiProfile> profiles = readProfiles();
            List<ApiProfile> updated = new ArrayList<>();
            boolean replaced = false;
            for (ApiProfile existing : profiles) {
                if (existing.getId().equals(profile.getId())) {
                    updated.add(profile);
                    replaced = true;
                } else {
                    updated.add(existing);
                }
            }
            if (!replaced) {
                updated.add(profile);
            }
            writeProfiles(updated);
            if (profile.getConfig().getApiKey() != null
                    && !profile.getConfig().getApiKey().isEmpty()) {
                secretStore.putSecret(KEY_ALIAS_PREFIX + profile.getId(),
                        profile.getConfig().getApiKey());
            }
            String activeId = prefs.getString(KEY_ACTIVE_PROFILE, null);
            boolean activatesEffectiveConfig = activeId == null
                    || activeId.equals(profile.getId());
            if (activatesEffectiveConfig) {
                prefs.edit().putString(KEY_ACTIVE_PROFILE, activeId == null
                        ? profile.getId() : activeId).apply();
                notifyConfigChanged(readActiveConfig());
            }
        }
    }

    @Nullable
    @Override
    public String deleteProfile(String profileId) {
        if (profileId == null) {
            return "profile not found";
        }
        synchronized (profileLock) {
            List<ApiProfile> profiles = readProfiles();
            if (profiles.size() <= 1) {
                return "至少保留一个配置";
            }
            List<ApiProfile> updated = new ArrayList<>();
            boolean found = false;
            for (ApiProfile profile : profiles) {
                if (profile.getId().equals(profileId)) {
                    found = true;
                } else {
                    updated.add(profile);
                }
            }
            if (!found) {
                return "profile not found";
            }
            writeProfiles(updated);
            secretStore.removeSecret(KEY_ALIAS_PREFIX + profileId);
            if (profileId.equals(prefs.getString(KEY_ACTIVE_PROFILE, null))) {
                String nextActive = updated.get(0).getId();
                prefs.edit().putString(KEY_ACTIVE_PROFILE, nextActive).apply();
                notifyConfigChanged(readActiveConfig());
            }
            return null;
        }
    }

    @Nullable
    @Override
    public String setActiveProfile(String profileId) {
        if (profileId == null) {
            return "profile not found";
        }
        synchronized (profileLock) {
            for (ApiProfile profile : readProfiles()) {
                if (profile.getId().equals(profileId)) {
                    prefs.edit().putString(KEY_ACTIVE_PROFILE, profileId).apply();
                    notifyConfigChanged(profile.getConfig());
                    return null;
                }
            }
            return "profile not found";
        }
    }

    @Override
    public int getContextRecentCount() {
        return prefs.getInt(KEY_CONTEXT_RECENT, 40);
    }

    @Override
    public void setContextRecentCount(int count) {
        prefs.edit().putInt(KEY_CONTEXT_RECENT, count).apply();
    }

    // ---------- 档案持久化 ----------

    /** 把旧的单配置迁移为一个档案（仅一次；旧键保留便于回滚）。 */
    private void migrateLegacyConfigIfNeeded() {
        if (prefs.getBoolean(KEY_PROFILES_MIGRATED, false)
                && prefs.contains(KEY_PROFILES_JSON)) {
            return;
        }
        boolean hasLegacy = prefs.contains(KEY_PROVIDER) || prefs.contains(KEY_BASE_URL)
                || secretStore.hasSecret(KEY_API_KEY);
        ApiProfile legacy;
        if (hasLegacy) {
            ApiConfig config = readConfig();
            legacy = new ApiProfile(ApiProfile.LEGACY_ID, "默认配置", config);
        } else {
            legacy = new ApiProfile(ApiProfile.LEGACY_ID, "默认配置", defaultConfig());
        }
        List<ApiProfile> profiles = new ArrayList<>();
        profiles.add(legacy);
        prefs.edit()
                .putString(KEY_PROFILES_JSON, toJson(profiles))
                .putString(KEY_ACTIVE_PROFILE, legacy.getId())
                .putBoolean(KEY_PROFILES_MIGRATED, true)
                .apply();
        // 迁移期不删除旧密钥键：legacy 档案继续读默认别名作为兜底。
        String legacyKey = secretStore.getSecret(KEY_API_KEY);
        if (hasLegacy && legacyKey != null && !legacyKey.isEmpty()
                && secretStore.getSecret(KEY_ALIAS_PREFIX + ApiProfile.LEGACY_ID) == null) {
            secretStore.putSecret(KEY_ALIAS_PREFIX + ApiProfile.LEGACY_ID, legacyKey);
        }
    }

    private List<ApiProfile> readProfiles() {
        String json = prefs.getString(KEY_PROFILES_JSON, null);
        List<ApiProfile> profiles = new ArrayList<>();
        if (json != null) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    ApiProfile profile = fromJson(array.optJSONObject(i));
                    if (profile != null) {
                        profiles.add(profile);
                    }
                }
            } catch (JSONException e) {
                // 档案数据损坏：回退到内置默认档案。
                profiles.clear();
            }
        }
        if (profiles.isEmpty()) {
            profiles.add(new ApiProfile(ApiProfile.LEGACY_ID, "默认配置", defaultConfig()));
        }
        return profiles;
    }

    private void writeProfiles(List<ApiProfile> profiles) {
        prefs.edit().putString(KEY_PROFILES_JSON, toJson(profiles)).apply();
    }

    private ApiConfig readActiveConfig() {
        return getActiveProfile().getConfig();
    }

    /** 密钥读取：先按档案别名，缺失时兜底旧默认别名（迁移期兼容）。 */
    @Nullable
    private String loadSecretForProfile(String profileId) {
        String secret = secretStore.getSecret(KEY_ALIAS_PREFIX + profileId);
        if (secret == null && ApiProfile.LEGACY_ID.equals(profileId)) {
            secret = secretStore.getSecret(KEY_API_KEY);
        }
        return secret;
    }

    private ApiProfile builtinDefaultProfile() {
        return new ApiProfile(ApiProfile.LEGACY_ID, "默认配置", defaultConfig());
    }

    private ApiConfig defaultConfig() {
        return new ApiConfig(ApiConfig.Provider.DEEPSEEK,
                defaultBaseUrl(ApiConfig.Provider.DEEPSEEK), null,
                defaultModel(ApiConfig.Provider.DEEPSEEK), 0.8f, 0.9f, 2048);
    }

    private void notifyConfigChanged(ApiConfig config) {
        configLiveData.postValue(config);
        ApiConfigChangeListener listener = configChangeListener;
        if (listener != null) {
            listener.onChanged(config);
        }
    }

    // ---------- org.json 序列化（不含密钥） ----------

    private static String toJson(List<ApiProfile> profiles) {
        JSONArray array = new JSONArray();
        for (ApiProfile profile : profiles) {
            try {
                array.put(toJson(profile));
            } catch (JSONException e) {
                // 单个档案序列化失败跳过，不影响其余档案。
            }
        }
        return array.toString();
    }

    private static JSONObject toJson(ApiProfile profile) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", profile.getId());
        obj.put("name", profile.getName());
        obj.put("provider", profile.getConfig().getProvider().name());
        obj.put("baseUrl", profile.getConfig().getBaseUrl());
        obj.put("model", profile.getConfig().getModel());
        obj.put("temperature", (double) profile.getConfig().getTemperature());
        obj.put("topP", (double) profile.getConfig().getTopP());
        obj.put("maxTokens", profile.getConfig().getMaxTokens());
        return obj;
    }

    @Nullable
    private ApiProfile fromJson(@Nullable JSONObject obj) {
        if (obj == null) {
            return null;
        }
        String id = obj.optString("id", null);
        if (id == null || id.isEmpty()) {
            return null;
        }
        ApiConfig.Provider provider;
        try {
            provider = ApiConfig.Provider.valueOf(obj.optString("provider",
                    ApiConfig.Provider.DEEPSEEK.name()));
        } catch (IllegalArgumentException e) {
            provider = ApiConfig.Provider.DEEPSEEK;
        }
        String baseUrl = obj.optString("baseUrl", defaultBaseUrl(provider));
        String model = obj.optString("model", defaultModel(provider));
        ApiConfig config = new ApiConfig(provider, baseUrl, loadSecretForProfile(id),
                model, (float) obj.optDouble("temperature", 0.8),
                (float) obj.optDouble("topP", 0.9), obj.optInt("maxTokens", 2048));
        return new ApiProfile(id, obj.optString("name", "配置"), config);
    }

    // ---------- 旧单配置读取（迁移来源与兜底） ----------

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
