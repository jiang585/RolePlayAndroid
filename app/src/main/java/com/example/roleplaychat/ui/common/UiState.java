package com.example.roleplaychat.ui.common;

import androidx.annotation.Nullable;

import com.example.roleplaychat.domain.model.AppError;

/**
 * 通用 UI 状态（架构文档 §10.2 标准）。
 */
public final class UiState<T> {

    private final boolean loading;
    @Nullable
    private final T data;
    @Nullable
    private final AppError error;

    private UiState(boolean loading, @Nullable T data, @Nullable AppError error) {
        this.loading = loading;
        this.data = data;
        this.error = error;
    }

    public static <T> UiState<T> loading() {
        return new UiState<>(true, null, null);
    }

    public static <T> UiState<T> success(T data) {
        return new UiState<>(false, data, null);
    }

    public static <T> UiState<T> error(AppError error) {
        return new UiState<>(false, null, error);
    }

    public boolean isLoading() {
        return loading;
    }

    @Nullable
    public T getData() {
        return data;
    }

    @Nullable
    public AppError getError() {
        return error;
    }
}
