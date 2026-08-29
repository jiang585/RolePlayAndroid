package com.example.roleplaychat.data.file;

import androidx.annotation.Nullable;

import com.example.roleplaychat.data.mapper.CharacterCardMapper;
import com.example.roleplaychat.data.mapper.TavernCharacterMapper;
import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.AppErrorCode;
import com.example.roleplaychat.util.JsonUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 导入读取器（架构文档 §9）：读取 -> 类型识别 -> 安全校验 -> 字段映射。
 * 所有输入视为不可信；JSON 深度与大小受限。
 */
public final class ImportReader {

    public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

    private ImportReader() {
    }

    public enum CardKind {
        CUSTOM,
        TAVERN,
        UNKNOWN
    }

    /** 读取并识别角色卡类型。 */
    public static CardKind detectCardKind(File file) {
        String text = readLimited(file);
        if (text == null) {
            return CardKind.UNKNOWN;
        }
        if (!JsonUtils.isSafeJson(text)) {
            return CardKind.UNKNOWN;
        }
        try {
            CharacterCardMapper.Envelope envelope = JsonUtils.fromJson(text, CharacterCardMapper.Envelope.class);
            if (envelope != null && envelope.format != null && envelope.format.contains("roleplay-character-card")) {
                return CardKind.CUSTOM;
            }
            TavernCharacterMapper.TavernCard tavern = JsonUtils.fromJson(text, TavernCharacterMapper.TavernCard.class);
            if (tavern != null && tavern.data != null && tavern.data.name != null) {
                return CardKind.TAVERN;
            }
            TavernCharacterMapper.FlatCard flat = JsonUtils.fromJson(text, TavernCharacterMapper.FlatCard.class);
            if (flat != null && flat.name != null && !flat.name.isEmpty()) {
                return CardKind.TAVERN;
            }
        } catch (Exception e) {
            return CardKind.UNKNOWN;
        }
        return CardKind.UNKNOWN;
    }

    /** 解析角色卡为自定义 data；失败返回 null。 */
    @Nullable
    public static CharacterCardMapper.Data parseCard(File file, @Nullable AppError[] error) {
        String text = readLimited(file);
        if (text == null) {
            setError(error, AppErrorCode.IMPORT_INVALID, "file too large or unreadable");
            return null;
        }
        if (!JsonUtils.isSafeJson(text)) {
            setError(error, AppErrorCode.IMPORT_INVALID, "invalid json");
            return null;
        }
        CardKind kind = detectCardKind(file);
        try {
            if (kind == CardKind.CUSTOM) {
                CharacterCardMapper.Envelope envelope = JsonUtils.fromJson(text, CharacterCardMapper.Envelope.class);
                if (envelope == null || envelope.data == null || envelope.data.name == null) {
                    setError(error, AppErrorCode.IMPORT_INVALID, "missing data");
                    return null;
                }
                return envelope.data;
            }
            if (kind == CardKind.TAVERN) {
                TavernCharacterMapper.TavernCard tavern = JsonUtils.fromJson(text, TavernCharacterMapper.TavernCard.class);
                if (tavern != null && tavern.data != null) {
                    return TavernCharacterMapper.toCustomData(tavern.data);
                }
                TavernCharacterMapper.FlatCard flat = JsonUtils.fromJson(text, TavernCharacterMapper.FlatCard.class);
                if (flat != null) {
                    return TavernCharacterMapper.toCustomData(flat);
                }
            }
        } catch (Exception e) {
            setError(error, AppErrorCode.IMPORT_INVALID, e.getMessage());
            return null;
        }
        setError(error, AppErrorCode.IMPORT_INVALID, "unsupported card format");
        return null;
    }

    /** 读取普通 JSON 信封（世界观/聊天），返回解析后的字符串；失败返回 null。 */
    @Nullable
    public static String readJson(File file, @Nullable AppError[] error) {
        String text = readLimited(file);
        if (text == null) {
            setError(error, AppErrorCode.IMPORT_INVALID, "file too large or unreadable");
            return null;
        }
        if (!JsonUtils.isSafeJson(text)) {
            setError(error, AppErrorCode.IMPORT_INVALID, "invalid json");
            return null;
        }
        return text;
    }

    /** 读取文件文本（限制大小）。 */
    @Nullable
    public static String readLimited(File file) {
        if (file == null || !file.exists() || file.length() > MAX_FILE_BYTES) {
            return null;
        }
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static void setError(@Nullable AppError[] error, AppErrorCode code, String message) {
        if (error != null && error.length > 0) {
            error[0] = AppError.of(code, message, false);
        }
    }
}
