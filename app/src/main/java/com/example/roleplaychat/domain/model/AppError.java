package com.example.roleplaychat.domain.model;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 应用错误（架构文档 §8.8 错误分类）。
 */
public final class AppError {

    private final AppErrorCode code;
    @Nullable
    private final String message;
    private final boolean retryable;

    public AppError(AppErrorCode code, @Nullable String message, boolean retryable) {
        this.code = Objects.requireNonNull(code);
        this.message = message;
        this.retryable = retryable;
    }

    public AppErrorCode getCode() {
        return code;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public static AppError of(AppErrorCode code) {
        return new AppError(code, null, false);
    }

    public static AppError of(AppErrorCode code, boolean retryable) {
        return new AppError(code, null, retryable);
    }

    public static AppError of(AppErrorCode code, @Nullable String message, boolean retryable) {
        return new AppError(code, message, retryable);
    }
}
