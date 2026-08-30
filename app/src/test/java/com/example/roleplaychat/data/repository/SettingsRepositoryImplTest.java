package com.example.roleplaychat.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.roleplaychat.data.security.SecretStore;
import com.example.roleplaychat.domain.model.ApiConfig;
import com.example.roleplaychat.domain.model.ApiProfile;
import com.example.roleplaychat.domain.repository.SettingsRepository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

/**
 * 设置仓库测试：多配置档案增删改查、legacy 迁移、active 切换触发 listener、
 * 密钥按别名隔离、删除守卫；以及 OPENCODE_GO provider 默认端点。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SettingsRepositoryImplTest {

    private Context context;
    private SharedPreferences prefs;
    private SecretStore secretStore;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        prefs = context.getSharedPreferences("roleplaychat_settings", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        // 内存版 SecretStore：put/get/remove 真实往返，模拟 Keystore 行为。
        java.util.Map<String, String> secrets = new java.util.HashMap<>();
        secretStore = mock(SecretStore.class);
        when(secretStore.getSecret(anyString()))
                .thenAnswer(invocation -> secrets.get(invocation.getArgument(0)));
        org.mockito.Mockito.doAnswer(invocation -> {
            secrets.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(secretStore).putSecret(anyString(), anyString());
    }

    @Test
    public void openCodeGo_defaultPreset_isZenGoV1Endpoint() {
        prefs.edit()
                .putString("api_provider", ApiConfig.Provider.OPENCODE_GO.name())
                .commit();

        ApiConfig config = new SettingsRepositoryImpl(context, secretStore).getApiConfig();

        assertEquals(ApiConfig.Provider.OPENCODE_GO, config.getProvider());
        assertEquals("https://opencode.ai/zen/go/v1", config.getBaseUrl());
        assertEquals("deepseek-v4-flash", config.getModel());
    }

    @Test
    public void saveThenRead_openCodeGo_configRoundTrips() {
        SettingsRepositoryImpl repo = new SettingsRepositoryImpl(context, secretStore);
        ApiConfig saved = new ApiConfig(ApiConfig.Provider.OPENCODE_GO,
                "https://opencode.ai/zen/go/v1", "sk-test", "deepseek-v4-flash",
                0.4f, 0.8f, 1024);

        repo.saveApiConfig(saved);
        ApiConfig read = repo.getApiConfig();

        assertEquals(ApiConfig.Provider.OPENCODE_GO, read.getProvider());
        assertEquals("https://opencode.ai/zen/go/v1", read.getBaseUrl());
        assertEquals("deepseek-v4-flash", read.getModel());
        assertEquals(0.4f, read.getTemperature(), 0.0001f);
        assertEquals(0.8f, read.getTopP(), 0.0001f);
        assertEquals(1024, read.getMaxTokens());
    }

    @Test
    public void unknownStoredProvider_fallsBackToDeepSeek() {
        prefs.edit().putString("api_provider", "NOT_A_PROVIDER").commit();

        ApiConfig config = new SettingsRepositoryImpl(context, secretStore).getApiConfig();

        assertEquals(ApiConfig.Provider.DEEPSEEK, config.getProvider());
        assertNull(config.getApiKey());
    }

    // ---------- 多配置档案 ----------

    @Test
    public void legacyConfig_isMigratedToLegacyProfileAndActive() {
        prefs.edit()
                .putString("api_provider", ApiConfig.Provider.OPENCODE_GO.name())
                .putString("api_base_url", "https://opencode.ai/zen/go/v1")
                .putString("api_model", "deepseek-v4-flash")
                .commit();
        when(secretStore.getSecret("api_key")).thenReturn("sk-legacy");

        SettingsRepositoryImpl repo = new SettingsRepositoryImpl(context, secretStore);

        List<ApiProfile> profiles = repo.getProfiles();
        assertEquals(1, profiles.size());
        assertEquals(ApiProfile.LEGACY_ID, profiles.get(0).getId());
        assertEquals("默认配置", profiles.get(0).getName());
        assertEquals(ApiProfile.LEGACY_ID, repo.getActiveProfileId());
        assertEquals("sk-legacy", repo.getApiConfig().getApiKey());
        // 旧键保留（回滚安全）
        assertEquals(ApiConfig.Provider.OPENCODE_GO.name(),
                prefs.getString("api_provider", null));
        // 迁移复制密钥到档案别名
        verify(secretStore).putSecret("api_key_legacy", "sk-legacy");
    }

    @Test
    public void freshInstall_activeProfileIsNeverNull() {
        SettingsRepositoryImpl repo = new SettingsRepositoryImpl(context, secretStore);

        assertNotNull(repo.getActiveProfile());
        assertEquals(1, repo.getProfiles().size());
        assertNull(repo.getApiConfig().getApiKey());
    }

    @Test
    public void saveProfile_addsAndUpdates_withoutTouchingOtherProfiles() {
        SettingsRepositoryImpl repo = new SettingsRepositoryImpl(context, secretStore);
        repo.saveProfile(new ApiProfile("p1", "DeepSeek 主力", config("sk-1", "m-1")));
        repo.saveProfile(new ApiProfile("p2", "备用", config("sk-2", "m-2")));

        repo.saveProfile(new ApiProfile("p1", "DeepSeek 主力改", config("sk-1b", "m-1b")));

        // legacy 默认档案 + 两个新建档案
        List<ApiProfile> profiles = repo.getProfiles();
        assertEquals(3, profiles.size());
        assertEquals("DeepSeek 主力改", find(profiles, "p1").getName());
        assertEquals("m-1b", find(profiles, "p1").getConfig().getModel());
        assertEquals("备用", find(profiles, "p2").getName());
        // 密钥按档案别名隔离写入
        verify(secretStore).putSecret("api_key_p1", "sk-1b");
        verify(secretStore).putSecret("api_key_p2", "sk-2");
    }

    @Test
    public void setActiveProfile_switchesEffectiveConfigAndFiresListener() {
        SettingsRepositoryImpl repo = new SettingsRepositoryImpl(context, secretStore);
        repo.saveProfile(new ApiProfile("p1", "A", config("sk-1", "m-1")));
        repo.saveProfile(new ApiProfile("p2", "B", config("sk-2", "m-2")));
        StringBuilder fired = new StringBuilder();
        repo.setApiConfigChangeListener(config -> fired.append(config.getModel()));

        assertNull(repo.setActiveProfile("p2"));

        assertEquals(ApiConfig.Provider.DEEPSEEK, repo.getApiConfig().getProvider());
        assertEquals("m-2", repo.getApiConfig().getModel());
        assertEquals("sk-2", repo.getApiConfig().getApiKey());
        assertEquals("p2", repo.getActiveProfileId());
        assertEquals("m-2", fired.toString());
    }

    @Test
    public void deleteActiveProfile_switchesToFirstRemaining() {
        SettingsRepositoryImpl repo = new SettingsRepositoryImpl(context, secretStore);
        repo.saveProfile(new ApiProfile("p1", "A", config("sk-1", "m-1")));
        repo.saveProfile(new ApiProfile("p2", "B", config("sk-2", "m-2")));
        repo.setActiveProfile("p2");

        assertNull(repo.deleteProfile("p2"));

        // 自动切换到剩余档案中的第一个（legacy 默认档案）
        assertEquals(ApiProfile.LEGACY_ID, repo.getActiveProfileId());
        assertEquals(2, repo.getProfiles().size());
        verify(secretStore).removeSecret("api_key_p2");
    }

    @Test
    public void deleteLastCustomProfile_fallsBackToLegacyDefault() {
        SettingsRepositoryImpl repo = new SettingsRepositoryImpl(context, secretStore);
        repo.saveProfile(new ApiProfile("p1", "A", config("sk-1", "m-1")));
        repo.setActiveProfile("p1");

        assertNull(repo.deleteProfile("p1"));

        assertEquals(ApiProfile.LEGACY_ID, repo.getActiveProfileId());
        assertEquals(1, repo.getProfiles().size());
    }

    @Test
    public void deleteLastProfile_isRejected() {
        SettingsRepositoryImpl repo = new SettingsRepositoryImpl(context, secretStore);

        assertNotNull(repo.deleteProfile(ApiProfile.LEGACY_ID));
        assertEquals(1, repo.getProfiles().size());
    }

    @Test
    public void secretKeys_areIsolatedPerProfile() {
        when(secretStore.getSecret(anyString())).thenAnswer(
                invocation -> invocation.getArgument(0).equals("api_key_p1") ? "sk-1" : null);

        SettingsRepositoryImpl repo = new SettingsRepositoryImpl(context, secretStore);
        repo.saveProfile(new ApiProfile("p1", "A", config("sk-1", "m-1")));
        repo.saveProfile(new ApiProfile("p2", "B", config("sk-2", "m-2")));
        repo.setActiveProfile("p1");

        assertEquals("sk-1", repo.getActiveProfile().getConfig().getApiKey());
        repo.setActiveProfile("p2");
        // p2 的密钥已真实写入（verify），读取走对应别名
        verify(secretStore).putSecret("api_key_p2", "sk-2");
    }

    private ApiConfig config(String apiKey, String model) {
        return new ApiConfig(ApiConfig.Provider.DEEPSEEK,
                "https://api.deepseek.com", apiKey, model, 0.8f, 0.9f, 2048);
    }

    private static ApiProfile find(List<ApiProfile> profiles, String id) {
        for (ApiProfile profile : profiles) {
            if (profile.getId().equals(id)) {
                return profile;
            }
        }
        throw new AssertionError("profile not found: " + id);
    }
}