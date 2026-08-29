package com.example.roleplaychat.domain.validation;

import com.example.roleplaychat.domain.model.ApiConfig;
import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.AppErrorCode;

import java.net.URI;

/**
 * API 配置校验（架构文档 §11.2）：
 * BaseURL 必须是合法 HTTPS URL（debug 可显式允许 HTTP）；模型必填；
 * temperature 0~2，top_p 0~1，max_tokens ≥1。
 */
public final class ApiConfigValidator {

    private ApiConfigValidator() {
    }

    public static AppError validateBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return AppError.of(AppErrorCode.VALIDATION_FAILED, "base url required", false);
        }
        try {
            URI uri = URI.create(baseUrl.trim());
            String scheme = uri.getScheme();
            if (scheme == null) {
                return AppError.of(AppErrorCode.VALIDATION_FAILED, "invalid url", false);
            }
            boolean debug = false;
            // release 仅 HTTPS；debug 构建允许局域网 HTTP（架构文档 §18-7）
            if (!scheme.equalsIgnoreCase("https")) {
                if (!debug || !scheme.equalsIgnoreCase("http")) {
                    return AppError.of(AppErrorCode.VALIDATION_FAILED, "https required", false);
                }
            }
        } catch (IllegalArgumentException e) {
            return AppError.of(AppErrorCode.VALIDATION_FAILED, "invalid url", false);
        }
        return null;
    }

    public static AppError validateModel(String model) {
        if (model == null || model.trim().isEmpty()) {
            return AppError.of(AppErrorCode.VALIDATION_FAILED, "model required", false);
        }
        return null;
    }

    public static AppError validateTemperature(float temperature) {
        if (temperature < 0f || temperature > 2f) {
            return AppError.of(AppErrorCode.VALIDATION_FAILED, "temperature out of range", false);
        }
        return null;
    }

    public static AppError validateTopP(float topP) {
        if (topP < 0f || topP > 1f) {
            return AppError.of(AppErrorCode.VALIDATION_FAILED, "top_p out of range", false);
        }
        return null;
    }

    public static AppError validateMaxTokens(int maxTokens) {
        if (maxTokens < 1) {
            return AppError.of(AppErrorCode.VALIDATION_FAILED, "max_tokens must be positive", false);
        }
        return null;
    }

    public static AppError validate(ApiConfig config) {
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            return AppError.of(AppErrorCode.VALIDATION_FAILED, "api key required", false);
        }
        AppError error = validateBaseUrl(config.getBaseUrl());
        if (error != null) {
            return error;
        }
        error = validateModel(config.getModel());
        if (error != null) {
            return error;
        }
        error = validateTemperature(config.getTemperature());
        if (error != null) {
            return error;
        }
        error = validateTopP(config.getTopP());
        if (error != null) {
            return error;
        }
        return validateMaxTokens(config.getMaxTokens());
    }
}
