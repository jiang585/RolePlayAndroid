package com.example.roleplaychat.domain.ai;

import androidx.annotation.Nullable;

import com.example.roleplaychat.domain.model.AiBatch;
import com.example.roleplaychat.domain.model.AiEvent;
import com.example.roleplaychat.util.JsonUtils;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构化输出解析器（架构文档 §8.5）：完整 JSON 解析。
 * 未闭合 JSON 不转换为正式消息（规则 10）；支持从 Markdown 代码围栏提取。
 */
public final class StructuredOutputParser {

    /** 解析失败异常。 */
    public static final class OutputInvalidException extends Exception {
        public OutputInvalidException(String message) {
            super(message);
        }
    }

    private static final Pattern FENCE_JSON = Pattern.compile("```(?:json)?\\s*\\n?(\\{.*?\\})\\s*```", Pattern.DOTALL);

    private StructuredOutputParser() {
    }

    /**
     * 解析完整响应文本为 AiBatch。
     *
     * @param raw       模型完整输出
     * @param requestId 期望的请求 ID
     */
    public static AiBatch parse(String raw, String requestId, String scriptId) throws OutputInvalidException {
        if (raw == null || raw.trim().isEmpty()) {
            throw new OutputInvalidException("empty output");
        }
        String json = extractJson(raw);
        StructuredOutput.Root root;
        try {
            root = JsonUtils.fromJson(json, StructuredOutput.Root.class);
        } catch (JsonSyntaxException e) {
            throw new OutputInvalidException("invalid json: " + e.getMessage());
        }
        if (root == null) {
            throw new OutputInvalidException("empty root");
        }
        if (root.schemaVersion != 1) {
            throw new OutputInvalidException("unsupported schema_version: " + root.schemaVersion);
        }
        // request_id is client-side correlation metadata. Older prompts asked the model to
        // echo it, but that made an otherwise valid response fail when the model omitted it.
        // The parsed batch always uses the trusted local requestId below.
        if (root.events == null || root.events.isEmpty()) {
            throw new OutputInvalidException("empty events");
        }
        List<AiEvent> events = new ArrayList<>();
        int index = 0;
        for (StructuredOutput.Event event : root.events) {
            if (event == null || event.content == null || event.content.trim().isEmpty()) {
                continue; // 空事件丢弃（§8.4）
            }
            AiEvent.Type type = parseType(event.type);
            if (type == null) {
                continue; // 未知类型丢弃
            }
            String eventId = event.eventId == null || event.eventId.trim().isEmpty()
                    ? "evt-" + index : event.eventId.trim();
            events.add(new AiEvent(
                    requestId + ":" + eventId,
                    type,
                    event.characterId,
                    event.content.trim(),
                    index++));
        }
        return new AiBatch(requestId, scriptId, events, root.continueScene);
    }

    /** 从原始文本中提取 JSON（兼容 Markdown 包裹，架构文档 §15.2-6）。 */
    public static String extractJson(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) {
            return trimmed;
        }
        Matcher matcher = FENCE_JSON.matcher(raw);
        if (matcher.find()) {
            return matcher.group(1);
        }
        // 兜底：从第一个 { 到最后一个 }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return trimmed;
    }

    /**
     * 结构化协议失败时提取可展示文本，避免 UI 提示“按文本显示”却没有消息。
     * 优先提取 JSON 中已有的 content 字段，否则返回清洗后的原文。
     */
    public static String fallbackText(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        Matcher content = Pattern.compile("\\\"content\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"")
                .matcher(text);
        StringBuilder extracted = new StringBuilder();
        while (content.find()) {
            try {
                String value = JsonUtils.fromJson("\"" + content.group(1) + "\"", String.class);
                if (value != null && !value.trim().isEmpty()) {
                    if (extracted.length() > 0) extracted.append('\n');
                    extracted.append(value.trim());
                }
            } catch (RuntimeException ignored) {
                // 继续尝试其他 content 字段
            }
        }
        if (extracted.length() > 0) {
            return limit(extracted.toString());
        }
        text = text.replaceFirst("^```(?:json|text)?\\s*", "")
                .replaceFirst("\\s*```$", "")
                .trim();
        return limit(text);
    }

    private static String limit(String text) {
        return text.length() > 4096 ? text.substring(0, 4096) : text;
    }

    @Nullable
    private static AiEvent.Type parseType(String type) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case "narration":
                return AiEvent.Type.NARRATION;
            case "character_turn":
                return AiEvent.Type.CHARACTER_TURN;
            case "system_event":
                return AiEvent.Type.SYSTEM_EVENT;
            default:
                return null;
        }
    }
}
