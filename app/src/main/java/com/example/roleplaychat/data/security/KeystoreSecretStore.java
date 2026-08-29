package com.example.roleplaychat.data.security;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Android Keystore 的加密存储（架构文档 §11.1）。
 * 使用 EncryptedSharedPreferences 保存 API Key 等敏感数据。
 */
public final class KeystoreSecretStore implements SecretStore {

    private static final String PREFS_NAME = "roleplaychat_secrets";
    private static final String KEY_ALIAS = "roleplaychat_master_key";

    @Nullable
    private final SharedPreferences preferences;
    private final Map<String, String> volatileSecrets = new ConcurrentHashMap<>();

    public KeystoreSecretStore(Context context) {
        this.preferences = openEncryptedPrefs(context);
    }

    KeystoreSecretStore(@Nullable SharedPreferences preferences) {
        this.preferences = preferences;
    }

    @Nullable
    private static SharedPreferences openEncryptedPrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            // Keystore 不可用时回退普通 SharedPreferences（尽力而为，不崩溃）。
            // Keystore 不可用时禁止明文落盘，仅保留进程内配置。
            return null;
        }
    }

    @Override
    public void putSecret(String key, String value) {
        if (preferences != null) {
            preferences.edit().putString(key, value).apply();
        } else if (value != null) {
            volatileSecrets.put(key, value);
        }
    }

    @Nullable
    @Override
    public String getSecret(String key) {
        return preferences == null ? volatileSecrets.get(key) : preferences.getString(key, null);
    }

    @Override
    public void removeSecret(String key) {
        if (preferences != null) {
            preferences.edit().remove(key).apply();
        }
        volatileSecrets.remove(key);
    }

    @Override
    public boolean hasSecret(String key) {
        return preferences == null ? volatileSecrets.containsKey(key) : preferences.contains(key);
    }
}
