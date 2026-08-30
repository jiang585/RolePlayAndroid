package com.example.roleplaychat.data.remote;

import com.example.roleplaychat.data.remote.interceptor.AuthInterceptor;
import com.example.roleplaychat.domain.model.ApiConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * OpenAI 兼容服务工厂（架构文档 §8）：构建 OkHttpClient 与 API。
 * OkHttp 日志不得记录 Authorization、完整 Prompt 与响应正文（§11.2）。
 */
public final class OpenAiServiceFactory {

    private final AuthInterceptor authInterceptor;
    private final OkHttpClient client;
    private volatile String baseUrl;
    private volatile String model;
    private volatile ApiConfig currentConfig;

    public OpenAiServiceFactory(ApiConfig initialConfig) {
        this.authInterceptor = new AuthInterceptor(initialConfig.getApiKey());
        this.baseUrl = normalizeBaseUrl(initialConfig.getBaseUrl());
        this.model = initialConfig.getModel();
        this.currentConfig = initialConfig;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(authInterceptor)
                .build();
    }

    /** 更新配置（运行时可调用）。 */
    public void updateConfig(ApiConfig config) {
        this.baseUrl = normalizeBaseUrl(config.getBaseUrl());
        this.model = config.getModel();
        this.currentConfig = config;
        this.authInterceptor.setApiKey(config.getApiKey());
    }

    public OkHttpClient okHttpClient() {
        return client;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String model() {
        return model;
    }

    public ApiConfig currentConfig() {
        return currentConfig;
    }

    public OpenAiCompatibleApi api() {
        return new retrofit2.Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()
                .create(OpenAiCompatibleApi.class);
    }

    /** 确保 BaseURL 以 / 结尾，兼容用户省略路径。 */
    public static String normalizeBaseUrl(String baseUrl) {
        String url = baseUrl == null || baseUrl.isEmpty() ? "https://api.openai.com/v1/" : baseUrl;
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        return url;
    }
}
