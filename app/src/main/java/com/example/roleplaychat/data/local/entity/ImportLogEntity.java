package com.example.roleplaychat.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 导入日志表（架构文档 §6.2 import_logs）。
 * 不得保存 API Key、完整隐藏设定或外部绝对路径。
 */
@Entity(tableName = "import_logs")
public class ImportLogEntity {

    @NonNull
    @PrimaryKey
    public String id;

    public String import_type;

    public String format_version;

    @Nullable
    public String source_display_name;

    public long imported_at;

    public boolean success;

    @Nullable
    public String error_code;

    public ImportLogEntity() {
    }

    public ImportLogEntity(String id, String importType, String formatVersion,
                           @Nullable String sourceDisplayName, long importedAt,
                           boolean success, @Nullable String errorCode) {
        this.id = id;
        this.import_type = importType;
        this.format_version = formatVersion;
        this.source_display_name = sourceDisplayName;
        this.imported_at = importedAt;
        this.success = success;
        this.error_code = errorCode;
    }
}
