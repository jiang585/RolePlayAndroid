package com.example.roleplaychat.domain.repository;

import androidx.annotation.Nullable;

import com.example.roleplaychat.domain.model.AppError;

import java.io.File;
import java.util.List;

/**
 * 导入导出仓库接口（架构文档 §9）。
 */
public interface ImportExportRepository {

    /** 导出角色卡到目标文件。includeHidden 为 true 时写入隐藏设定并警示。 */
    @Nullable
    AppError exportCharacterCard(String characterId, File target, boolean includeHidden);

    /** 导出世界观到目标文件。 */
    @Nullable
    AppError exportWorld(String scriptId, File target);

    /** 导出聊天记录（JSON）。 */
    @Nullable
    AppError exportChatJson(String scriptId, File target);

    /** 导出聊天记录（TXT 可读格式）。 */
    @Nullable
    AppError exportChatTxt(String scriptId, File target);

    AppError exportChatPdf(String scriptId, File target);

    /** 导出剧本包到目标目录。 */
    @Nullable
    AppError exportScriptPackage(String scriptId, File targetDir);

    /**
     * 导入角色卡到指定剧本。
     *
     * @return 新角色 ID，或 null 表示失败（错误通过 error 返回）
     */
    @Nullable
    String importCharacterCard(File source, String targetScriptId, @Nullable AppError[] error);

    /** 导入世界观，返回目标剧本 ID 或错误。 */
    @Nullable
    String importWorld(File source, String targetScriptId, @Nullable AppError[] error);

    /** 导入聊天记录（追加），返回错误或 null 表示成功。 */
    @Nullable
    AppError importChat(File source, String targetScriptId);

    /** 解析角色卡文件返回预览文本（用于导入确认页）。 */
    String previewCharacterCard(File source);

    List<String> supportedImportExtensions();
}
