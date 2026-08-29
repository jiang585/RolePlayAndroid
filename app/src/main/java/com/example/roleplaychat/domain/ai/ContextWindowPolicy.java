package com.example.roleplaychat.domain.ai;

import com.example.roleplaychat.domain.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文窗口策略（架构文档 §8.2）：
 * 由旧到新裁剪但始终保留最后一条玩家输入；世界规则、当前身份和最新消息不可裁剪。
 * MVP 采用保守的消息条数限制，不精确计算 token。
 */
public final class ContextWindowPolicy {

    private static final int MAX_CONTEXT_CHARS = 24000;
    private static final int MAX_MEMORY_CHARS = 7000;
    private static final int MAX_RECENT_PROMPT_CHARS = 15000;
    private static final int MAX_MEMORY_LINE_CHARS = 360;

    private ContextWindowPolicy() {
    }

    /**
     * 将消息转换为可读对话文本，按最近 N 条裁剪（默认 40）。
     *
     * @param messages    正序消息列表
     * @param recentCount 保留的最近消息条数
     */
    public static String toConversationText(List<ChatMessage> messages, int recentCount) {
        return toConversationText(messages, recentCount, MAX_CONTEXT_CHARS);
    }

    private static String toConversationText(List<ChatMessage> messages, int recentCount,
                                             int maxChars) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        int limit = Math.max(1, recentCount);
        int start = Math.max(0, messages.size() - limit);
        List<ChatMessage> window = new ArrayList<>(messages.subList(start, messages.size()));
        StringBuilder sb = new StringBuilder();
        for (int i = window.size() - 1; i >= 0; i--) {
            ChatMessage message = window.get(i);
            if (message.getStatus() == ChatMessage.Status.FAILED && message.getContent().isEmpty()) {
                continue;
            }
            String line = formatMessage(message);
            if (sb.length() + line.length() + 1 > maxChars) {
                break;
            }
            sb.insert(0, line + '\n');
        }
        return sb.toString().trim();
    }

    /**
     * 构建发给模型的完整聊天上下文。较早消息不会因最近窗口而直接消失，而是以有界的
     * 剧情记忆保留在请求中；最近消息仍保留原文，以免影响当前回合的细节。
     */
    public static String toPromptContext(List<ChatMessage> messages, int recentCount) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        int limit = Math.max(1, recentCount);
        int recentStart = Math.max(0, messages.size() - limit);
        String recent = toConversationText(messages.subList(recentStart, messages.size()), limit,
                MAX_RECENT_PROMPT_CHARS);
        String memory = toLongTermMemory(messages.subList(0, recentStart), MAX_MEMORY_CHARS);
        if (memory.isEmpty()) {
            return recent;
        }
        if (recent.isEmpty()) {
            return "【长期剧情记忆】\n" + memory;
        }
        return "【长期剧情记忆】\n"
                + "以下是已发生的较早剧情，必须视为既成事实，不要重复执行或叙述。\n"
                + memory + "\n\n【最近对话】\n" + recent;
    }

    private static String toLongTermMemory(List<ChatMessage> olderMessages, int maxChars) {
        List<String> lines = new ArrayList<>();
        for (ChatMessage message : olderMessages) {
            if (message.getStatus() == ChatMessage.Status.FAILED && message.getContent().isEmpty()) {
                continue;
            }
            String line = formatMessage(message);
            if (line.length() > MAX_MEMORY_LINE_CHARS) {
                line = line.substring(0, MAX_MEMORY_LINE_CHARS) + "...";
            }
            lines.add("#" + message.getSequence() + " " + line);
        }
        if (lines.isEmpty()) {
            return "";
        }

        // 同时保留剧情开端和紧接当前窗口前的状态，避免单纯取尾部再次遗忘早期约定。
        StringBuilder result = new StringBuilder();
        int headBudget = maxChars / 3;
        int headEnd = appendForward(lines, 0, lines.size(), headBudget, result);
        if (headEnd < lines.size()) {
            List<String> tail = new ArrayList<>();
            appendBackward(lines, lines.size() - 1, headEnd, maxChars - result.length(), tail);
            if (!tail.isEmpty()) {
                result.append("\n…（中间剧情已压缩）");
                for (int i = tail.size() - 1; i >= 0; i--) {
                    result.append('\n').append(tail.get(i));
                }
            }
        }
        return result.toString();
    }

    private static int appendForward(List<String> lines, int start, int end, int budget,
                                     StringBuilder out) {
        int index = start;
        while (index < end) {
            String line = lines.get(index);
            int addition = line.length() + (out.length() == 0 ? 0 : 1);
            if (out.length() + addition > budget) {
                break;
            }
            if (out.length() > 0) out.append('\n');
            out.append(line);
            index++;
        }
        return index;
    }

    private static void appendBackward(List<String> lines, int start, int lowerBound, int budget,
                                       List<String> out) {
        int used = 0;
        for (int i = start; i >= lowerBound; i--) {
            String line = lines.get(i);
            int addition = line.length() + (out.isEmpty() ? 0 : 1);
            if (used + addition > budget) break;
            out.add(line);
            used += addition;
        }
    }

    /** 格式化单条消息（角色名 + 内容；旁白/事件居中样式标记）。 */
    public static String formatMessage(ChatMessage message) {
        String sender;
        switch (message.getType()) {
            case NARRATION:
                return "（旁白）" + message.getContent();
            case SYSTEM_EVENT:
                return "【系统】" + message.getContent();
            case CHARACTER_TEXT:
            default:
                if (message.getSide() == ChatMessage.Side.MINE) {
                    sender = message.getSenderDisplayName() != null
                            ? message.getSenderDisplayName() : "我";
                    return sender + "：" + message.getContent();
                }
                sender = message.getSenderDisplayName() != null
                        ? message.getSenderDisplayName() : "角色";
                return sender + "：" + message.getContent();
        }
    }
}
