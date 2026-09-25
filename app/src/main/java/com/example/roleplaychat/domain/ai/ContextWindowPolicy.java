package com.example.roleplaychat.domain.ai;

import com.example.roleplaychat.domain.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文窗口策略（架构文档 §8.2）。
 *
 * <h2>为什么是「纪元」而不是滑动窗口</h2>
 * 服务端前缀缓存匹配的是最长公共前缀。传统「最近 N 条」窗口每轮整体平移一条，
 * 于是每轮上下文都被重排，缓存永远无法命中。这里改成确定性纪元：
 *
 * <ul>
 *   <li><b>尾部</b> = 原始消息 {@code [anchor, size)}，只追加、不重排、不裁剪；</li>
 *   <li><b>anchor 只取纪元粒度的整数倍</b>，因此在一段连续轮次内恒定；</li>
 *   <li>只有当尾部渲染超过字符预算时才把 anchor 前移一个纪元，让出的消息并入
 *       【长期剧情记忆】——于是失效频率从「每轮一次」降到「约每纪元一次」。</li>
 * </ul>
 *
 * <p>anchor 是「消息列表的纯函数」，不依赖任何持久化状态，因此进程重启后
 * 同一段历史仍会算出同一个 anchor，已建立的缓存不会因重启而作废。
 *
 * <p>锚点随消息增长<b>单调不减</b>：追加消息只会增加每个后缀的长度，
 * 满足预算的起点集合只会收缩，所以尾部永远不会反向变长。
 */
public final class ContextWindowPolicy {

    private static final int MAX_MEMORY_CHARS = 7000;
    private static final int MAX_TAIL_CHARS = 15000;
    /** 纪元粒度下限：设置过小会让锚点频繁前移，前缀缓存反复失效。 */
    private static final int MIN_EPOCH_MESSAGES = 20;

    private static final String MEMORY_HEADER = "【长期剧情记忆】";
    private static final String MEMORY_PREAMBLE =
            "以下是已发生的较早剧情，必须视为既成事实，不要重复执行或叙述。";
    private static final String TAIL_HEADER = "【最近对话】";

    private ContextWindowPolicy() {
    }

    /**
     * 构建只追加的剧情上下文：冻结的长期记忆 + 纪元起点到最新的原始消息。
     *
     * <p>同一纪元内，本方法的输出对上一轮而言<b>永远是前缀</b>，这是缓存命中的前提。
     *
     * @param messages      正序消息列表
     * @param epochMessages 纪元粒度（消息条数）；小于下限时按下限处理
     */
    public static String toHistoryContext(List<ChatMessage> messages, int epochMessages) {
        return toHistoryContext(messages, epochMessages, null);
    }

    /**
     * 使用已经生成的滚动摘要。只有摘要明确覆盖到当前纪元起点前一条消息时才采用，
     * 防止摘要请求尚未完成时把新剧情误当成已压缩内容。
     */
    public static String toHistoryContext(List<ChatMessage> messages, int epochMessages,
                                          ContextMemoryStore.Snapshot snapshot) {
        List<ChatMessage> renderable = renderable(messages);
        if (renderable.isEmpty()) {
            return "";
        }
        int anchor = epochAnchor(renderable, epochMessages);
        if (anchor > 0 && snapshot != null
                && snapshot.throughMessageId.equals(renderable.get(anchor - 1).getId())) {
            String tail = renderRange(renderable, anchor);
            return MEMORY_HEADER + "\n" + MEMORY_PREAMBLE + "\n"
                    + snapshot.summary + "\n\n" + TAIL_HEADER + "\n" + tail;
        }
        String memory = toLongTermMemory(renderable.subList(0, anchor), MAX_MEMORY_CHARS);
        String tail = renderRange(renderable, anchor);
        if (memory.isEmpty()) {
            return TAIL_HEADER + "\n" + tail;
        }
        return MEMORY_HEADER + "\n" + MEMORY_PREAMBLE + "\n"
                + memory + "\n\n" + TAIL_HEADER + "\n" + tail;
    }

    /**
     * 当前纪元起点（在可渲染消息中的下标）。是消息列表的纯函数，随消息增长单调不减。
     *
     * <p>两个前移触发条件，取更靠后的那个：
     * <ul>
     *   <li><b>条数</b>：尾部保留一到两个纪元的原始消息，对应「最近消息条数」设置；</li>
     *   <li><b>预算</b>：尾部渲染超过字符预算时再让出一个纪元。</li>
     * </ul>
     * 两者都向上对齐到纪元边界，因此锚点在一整段轮次里恒定，尾部逐轮只追加。
     */
    static int epochAnchor(List<ChatMessage> renderable, int epochMessages) {
        int size = renderable.size();
        if (size <= 1) {
            return 0;
        }
        int step = Math.max(MIN_EPOCH_MESSAGES, epochMessages);
        int countAnchor = size <= step ? 0 : ((size - step) / step) * step;
        int budgetAnchor = ((budgetAnchor(renderable) + step - 1) / step) * step;
        // 至少把最新一条留在尾部，否则模型看不到任何「正在发生」的原文。
        return Math.min(Math.max(countAnchor, budgetAnchor), size - 1);
    }

    /** 返回与上下文策略完全相同的可渲染消息，供摘要器定位边界。 */
    public static List<ChatMessage> renderableMessages(List<ChatMessage> messages) {
        return renderable(messages);
    }

    /** 当前纪元起点，供后台滚动摘要器使用。 */
    public static int currentEpochAnchor(List<ChatMessage> messages, int epochMessages) {
        return epochAnchor(renderable(messages), epochMessages);
    }

    /** 从末尾向前累积，返回使尾部渲染不超过字符预算的最小起点（未对齐纪元边界）。 */
    private static int budgetAnchor(List<ChatMessage> renderable) {
        int used = 0;
        for (int i = renderable.size() - 1; i >= 0; i--) {
            int lineLength = formatMessage(renderable.get(i)).length() + 1;
            if (used + lineLength > MAX_TAIL_CHARS) {
                return i + 1;
            }
            used += lineLength;
        }
        return 0;
    }

    private static String renderRange(List<ChatMessage> renderable, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < renderable.size(); i++) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(formatMessage(renderable.get(i)));
        }
        return sb.toString();
    }

    /** 失败且无内容的消息不参与渲染；过滤结果只取决于消息自身，因此不破坏锚点确定性。 */
    private static List<ChatMessage> renderable(List<ChatMessage> messages) {
        List<ChatMessage> result = new ArrayList<>();
        if (messages == null) {
            return result;
        }
        for (ChatMessage message : messages) {
            if (message.getStatus() == ChatMessage.Status.FAILED && message.getContent().isEmpty()) {
                continue;
            }
            result.add(message);
        }
        return result;
    }

    private static String toLongTermMemory(List<ChatMessage> olderMessages, int maxChars) {
        List<String> lines = new ArrayList<>();
        for (ChatMessage message : olderMessages) {
            String line = formatMessage(message);
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
