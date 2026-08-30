package com.example.roleplaychat.domain.model;

import java.util.Objects;

/**
 * AI 接口配置档案：一份命名的 {@link ApiConfig}。
 * id 创建时生成（UUID）；密钥不落库本模型，按档案 id 隔离存于 SecretStore。
 */
public final class ApiProfile {

    /** 旧单配置迁移生成的固定档案 id。 */
    public static final String LEGACY_ID = "legacy";

    private final String id;
    private final String name;
    private final ApiConfig config;

    public ApiProfile(String id, String name, ApiConfig config) {
        this.id = Objects.requireNonNull(id);
        this.name = name == null || name.trim().isEmpty() ? "配置" : name.trim();
        this.config = Objects.requireNonNull(config);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ApiConfig getConfig() {
        return config;
    }
}
