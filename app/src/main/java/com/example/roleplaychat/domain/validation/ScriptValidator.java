package com.example.roleplaychat.domain.validation;

import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.AppErrorCode;

/**
 * 剧本校验（架构文档 §2.3/§14.1）：名称 1~80 字符，一句话最大 500 字符。
 */
public final class ScriptValidator {

    public static final int MAX_NAME = 80;
    public static final int MAX_ONE_LINE = 500;

    private ScriptValidator() {
    }

    public static AppError validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return AppError.of(AppErrorCode.VALIDATION_FAILED, "name required", false);
        }
        if (name.trim().length() > MAX_NAME) {
            return AppError.of(AppErrorCode.VALIDATION_FAILED, "name too long", false);
        }
        return null;
    }

    public static AppError validateOneLine(String oneLine) {
        if (oneLine != null && oneLine.length() > MAX_ONE_LINE) {
            return AppError.of(AppErrorCode.VALIDATION_FAILED, "one line too long", false);
        }
        return null;
    }
}
