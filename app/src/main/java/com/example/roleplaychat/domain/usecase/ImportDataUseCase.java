package com.example.roleplaychat.domain.usecase;

import androidx.annotation.Nullable;

import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.repository.ImportExportRepository;
import com.example.roleplaychat.domain.validation.ImportValidator;

import java.io.File;

/**
 * 导入数据用例（架构文档 §9.6）：预检 -> 预览确认 -> 事务写入 -> 资源本地化。
 * 由上层先展示 previewCharacterCard 预览，再调用 execute 确认导入。
 */
public class ImportDataUseCase {

    public enum ImportType {
        CHARACTER,
        WORLD,
        CHAT,
        SCRIPT_PACKAGE
    }

    public enum ImportMode {
        CREATE_NEW,
        MERGE,
        REPLACE
    }

    private final ImportExportRepository repository;

    public ImportDataUseCase(ImportExportRepository repository) {
        this.repository = repository;
    }

    /**
     * 执行导入。
     *
     * @return 成功结果（角色 ID / 剧本 ID / null for chat），失败为 null 且 error 非空。
     */
    @Nullable
    public String execute(ImportType type, File source, String targetScriptId,
                          ImportMode mode, @Nullable AppError[] error) {
        AppError validateError = ImportValidator.validateFile(source);
        if (validateError != null) {
            setError(error, validateError);
            return null;
        }
        switch (type) {
            case CHARACTER:
                return repository.importCharacterCard(source, targetScriptId, error);
            case WORLD:
                return repository.importWorld(source, targetScriptId, error);
            case CHAT:
                AppError chatError = repository.importChat(source, targetScriptId);
                if (chatError != null) {
                    setError(error, chatError);
                    return null;
                }
                return "";
            case SCRIPT_PACKAGE:
                setError(error, AppError.of(
                        com.example.roleplaychat.domain.model.AppErrorCode.IMPORT_INVALID,
                        "script package import not implemented in v1.0", false));
                return null;
            default:
                setError(error, AppError.of(
                        com.example.roleplaychat.domain.model.AppErrorCode.IMPORT_INVALID,
                        "unsupported import type", false));
                return null;
        }
    }

    /** 预览角色卡（导入确认页使用）。 */
    public String preview(File source) {
        return repository.previewCharacterCard(source);
    }

    private void setError(@Nullable AppError[] error, AppError appError) {
        if (error != null && error.length > 0) {
            error[0] = appError;
        }
    }
}
