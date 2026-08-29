package com.example.roleplaychat.domain.validation;

import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.AppErrorCode;

import java.io.File;

/**
 * 导入校验（架构文档 §9.7）：文件大小、可读性。JSON 深度在解析器中校验。
 */
public final class ImportValidator {

    public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

    private ImportValidator() {
    }

    public static AppError validateFile(File file) {
        if (file == null || !file.exists()) {
            return AppError.of(AppErrorCode.IMPORT_INVALID, "file not found", false);
        }
        if (!file.isFile()) {
            return AppError.of(AppErrorCode.IMPORT_INVALID, "not a file", false);
        }
        if (file.length() > MAX_FILE_BYTES) {
            return AppError.of(AppErrorCode.IMPORT_INVALID, "file too large", false);
        }
        if (file.length() == 0) {
            return AppError.of(AppErrorCode.IMPORT_INVALID, "empty file", false);
        }
        return null;
    }
}
