package com.example.roleplaychat.util;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 文件名净化（架构文档 §9.7）：移除路径分隔符、控制字符与保留字符，
 * 限制长度，保证任何外部输入都能安全地作为文件名使用。
 */
public final class FileNameSanitizer {

    private static final Pattern DANGEROUS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
    private static final int MAX_LENGTH = 80;

    private FileNameSanitizer() {
    }

    public static String sanitize(@Nullable String raw, String fallback) {
        String base = raw == null ? "" : raw.trim();
        String cleaned = DANGEROUS.matcher(base).replaceAll("_");
        cleaned = cleaned.replaceAll("\\.{2,}", "_");
        cleaned = cleaned.replaceAll("^[. ]+|[. ]+$", "");
        cleaned = cleaned.replaceAll("\\s+", "_");
        if (cleaned.isEmpty()) {
            cleaned = fallback;
        }
        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH);
        }
        return cleaned.toLowerCase(Locale.ROOT);
    }
}
