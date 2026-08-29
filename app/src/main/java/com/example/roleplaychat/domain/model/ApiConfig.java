package com.example.roleplaychat.domain.model;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * AI 接口配置（架构文档 §11.2）。API Key 由 {@code SecretStore} 加密保存，
 * 本模型中的 apiKey 仅在内存中传递，不入库。
 */
public final class ApiConfig {
    public enum Provider {
        DEEPSEEK,
        OPENAI_COMPATIBLE,
        /** OpenCode Go（OpenCode Zen 低价订阅计划），OpenAI 兼容端点。 */
        OPENCODE_GO
    }

    private final Provider provider;
    private final String baseUrl;
    @Nullable
    private final String apiKey;
    private final String model;
    private final float temperature;
    private final float topP;
    private final int maxTokens;

    public ApiConfig(String baseUrl, @Nullable String apiKey, String model,
                     float temperature, float topP, int maxTokens) {
        this(Provider.OPENAI_COMPATIBLE, baseUrl, apiKey, model, temperature, topP, maxTokens);
    }

    public ApiConfig(Provider provider, String baseUrl, @Nullable String apiKey, String model,
                     float temperature, float topP, int maxTokens) {
        this.provider = provider == null ? Provider.OPENAI_COMPATIBLE : provider;
        this.baseUrl = Objects.requireNonNull(baseUrl);
        this.apiKey = apiKey;
        this.model = Objects.requireNonNull(model);
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Provider getProvider() {
        return provider;
    }

    @Nullable
    public String getApiKey() {
        return apiKey;
    }

    public String getModel() {
        return model;
    }

    public float getTemperature() {
        return temperature;
    }

    public float getTopP() {
        return topP;
    }

    public int getMaxTokens() {
        return maxTokens;
    }
}
