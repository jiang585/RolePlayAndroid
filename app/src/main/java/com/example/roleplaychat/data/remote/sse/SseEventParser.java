package com.example.roleplaychat.data.remote.sse;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * SSE 事件解析器（架构文档 §8.5）：处理 data 前缀、多行 data、事件分隔、
 * 空事件与 [DONE]。字节流由上层逐行喂入。
 */
public final class SseEventParser {

    private static final String DATA_PREFIX = "data:";
    private static final String DONE = "[DONE]";

    private final StringBuilder dataBuffer = new StringBuilder();
    private boolean done;

    /** 喂入一行原始文本（不含换行符）。返回该行产生的事件负载；null 表示无完整事件。 */
    @Nullable
    public String onLine(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            // 空行结束当前事件
            return flushEvent();
        }
        if (trimmed.startsWith(":")) {
            // 注释行忽略
            return null;
        }
        if (trimmed.startsWith(DATA_PREFIX)) {
            String value = trimmed.substring(DATA_PREFIX.length());
            if (value.startsWith(" ")) {
                value = value.substring(1);
            }
            if (dataBuffer.length() > 0) {
                dataBuffer.append('\n');
            }
            dataBuffer.append(value);
            return null;
        }
        // 其他字段（event:/id:/retry:）忽略
        return null;
    }

    /** 流结束时强制刷新剩余事件。 */
    @Nullable
    public String finish() {
        return flushEvent();
    }

    /** 收到协议终止标记后为 true，上层应立即结束读取而不是等待服务端断开。 */
    public boolean isDone() {
        return done;
    }

    @Nullable
    private String flushEvent() {
        if (dataBuffer.length() == 0) {
            return null;
        }
        String payload = dataBuffer.toString();
        dataBuffer.setLength(0);
        String trimmed = payload.trim();
        if (DONE.equals(trimmed)) {
            done = true;
            return null;
        }
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    /** 便捷方法：解析完整 SSE 文本（测试用）。 */
    public static List<String> parseAll(String sseText) {
        SseEventParser parser = new SseEventParser();
        List<String> events = new ArrayList<>();
        for (String line : sseText.split("\n")) {
            String event = parser.onLine(line);
            if (event != null) {
                events.add(event);
            }
        }
        String last = parser.finish();
        if (last != null) {
            events.add(last);
        }
        return events;
    }
}
