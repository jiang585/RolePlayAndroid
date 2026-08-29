package com.example.roleplaychat.domain.model;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 稳定错误码（架构文档 §8.8）。不存储敏感原文，只存分类。
 */
public enum AppErrorCode {
    NETWORK_UNAVAILABLE("NETWORK_UNAVAILABLE"),
    AUTH_INVALID("AUTH_INVALID"),
    RATE_LIMITED("RATE_LIMITED"),
    MODEL_NOT_FOUND("MODEL_NOT_FOUND"),
    OUTPUT_INVALID("OUTPUT_INVALID"),
    UNKNOWN_CHARACTER("UNKNOWN_CHARACTER"),
    CANCELLED_BY_USER("CANCELLED_BY_USER"),
    PROCESS_INTERRUPTED("PROCESS_INTERRUPTED"),
    VALIDATION_FAILED("VALIDATION_FAILED"),
    IMPORT_INVALID("IMPORT_INVALID"),
    UNKNOWN("UNKNOWN");

    private final String code;

    AppErrorCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 从稳定代码解析；未知代码返回 UNKNOWN，不崩溃（架构文档 §5.3）。 */
    public static AppErrorCode fromCode(@Nullable String code) {
        if (code == null) {
            return UNKNOWN;
        }
        for (AppErrorCode value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
