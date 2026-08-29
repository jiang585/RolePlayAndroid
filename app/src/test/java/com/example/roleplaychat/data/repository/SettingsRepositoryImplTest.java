package com.example.roleplaychat.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.roleplaychat.data.security.SecretStore;
import com.example.roleplaychat.domain.model.ApiConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * 设置仓库测试：新增的 OPENCODE_GO provider 默认端点（opencode.ai/docs/go）
 * 与保存/读取往返。
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
        secretStore = mock(SecretStore.class);
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
}