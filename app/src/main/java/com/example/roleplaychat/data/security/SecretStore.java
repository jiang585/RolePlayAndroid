package com.example.roleplaychat.data.security;

import androidx.annotation.Nullable;

/**
 * 敏感数据存储接口（架构文档 §11.1）：API Key 等使用 Keystore 加密。
 */
public interface SecretStore {

    void putSecret(String key, String value);

    @Nullable
    String getSecret(String key);

    void removeSecret(String key);

    /** 是否存在明文外的加密存储。 */
    boolean hasSecret(String key);
}
