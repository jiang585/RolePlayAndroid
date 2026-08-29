package com.example.roleplaychat.domain.usecase;

import androidx.annotation.Nullable;

import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.repository.ImportExportRepository;

import java.io.File;

/**
 * 导出数据用例（架构文档 §9）。导出文件由用户主动创建和分享（§11.3）。
 */
public class ExportDataUseCase {

    public enum ExportType {
        CHARACTER,
        WORLD,
        CHAT_JSON,
        CHAT_TXT,
        CHAT_PDF,
        SCRIPT_PACKAGE
    }

    private final ImportExportRepository repository;

    public ExportDataUseCase(ImportExportRepository repository) {
        this.repository = repository;
    }

    /** @return null 表示成功。 */
    @Nullable
    public AppError execute(ExportType type, String scriptId, String characterId,
                            File target, boolean includeHidden) {
        switch (type) {
            case CHARACTER:
                return repository.exportCharacterCard(characterId, target, includeHidden);
            case WORLD:
                return repository.exportWorld(scriptId, target);
            case CHAT_JSON:
                return repository.exportChatJson(scriptId, target);
            case CHAT_TXT:
                return repository.exportChatTxt(scriptId, target);
            case CHAT_PDF:
                return repository.exportChatPdf(scriptId, target);
            case SCRIPT_PACKAGE:
                return repository.exportScriptPackage(scriptId, target);
            default:
                return AppError.of(com.example.roleplaychat.domain.model.AppErrorCode.UNKNOWN,
                        "unsupported export type", false);
        }
    }
}
