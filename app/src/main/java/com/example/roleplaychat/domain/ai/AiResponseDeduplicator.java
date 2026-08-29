package com.example.roleplaychat.domain.ai;

import com.example.roleplaychat.domain.model.AiBatch;
import com.example.roleplaychat.domain.model.AiEvent;
import com.example.roleplaychat.domain.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/** 自动续演的幂等保护：拒绝紧邻历史消息的高度相似复读。 */
public final class AiResponseDeduplicator {

    private static final double SIMILARITY_THRESHOLD = 0.86d;
    /** 历史回看时最多比较的同类型最近消息条数（防止长历史下编辑距离计算失控）。 */
    private static final int MAX_HISTORY_MATCHES = 4;

    private AiResponseDeduplicator() {
    }

    public static AiBatch removeNearDuplicates(AiBatch candidate, List<ChatMessage> history) {
        if (candidate == null || candidate.isEmpty()) {
            return candidate;
        }
        List<AiEvent> kept = new ArrayList<>();
        for (AiEvent event : candidate.getEvents()) {
            boolean duplicate = false;
            // 1) 与历史中最近几条约定同类型消息比较，不提前 break：
            //    系统事件等短内容可能不是最近一条同类型消息，但仍可能是复读。
            if (history != null) {
                int sameSeen = 0;
                for (int i = history.size() - 1; i >= 0 && sameSeen < MAX_HISTORY_MATCHES; i--) {
                    ChatMessage previous = history.get(i);
                    if (!sameStream(event, previous)) {
                        continue;
                    }
                    sameSeen++;
                    if (isNearDuplicate(event.getContent(), previous.getContent())) {
                        duplicate = true;
                        break;
                    }
                }
            }
            // 2) 与同批次内已保留的事件比较：一次回复里模型可能把同一事件输出两遍。
            if (!duplicate) {
                for (AiEvent previous : kept) {
                    if (sameStream(event, previous)
                            && isNearDuplicate(event.getContent(), previous.getContent())) {
                        duplicate = true;
                        break;
                    }
                }
            }
            if (!duplicate) {
                kept.add(event);
            }
        }
        return new AiBatch(candidate.getRequestId(), candidate.getScriptId(), kept, kept.isEmpty()
                ? false : candidate.shouldContinueScene());
    }

    private static boolean isNearDuplicate(String content, String other) {
        String left = normalize(content);
        String right = normalize(other);
        int maxLen = Math.max(left.length(), right.length());
        if (maxLen == 0) {
            return true;
        }
        // 长度差超过阈值允许的最大编辑距离时，相似度必然低于阈值，跳过昂贵的计算。
        if (Math.abs(left.length() - right.length()) > (1d - SIMILARITY_THRESHOLD) * maxLen) {
            return false;
        }
        return similarityNormalized(left, right) >= SIMILARITY_THRESHOLD;
    }

    private static boolean sameStream(AiEvent event, ChatMessage previous) {
        if (event.getType() == AiEvent.Type.CHARACTER_TURN) {
            return previous.getType() == ChatMessage.Type.CHARACTER_TEXT
                    && previous.getSide() == ChatMessage.Side.THEIRS
                    && java.util.Objects.equals(event.getCharacterId(), previous.getCharacterId());
        }
        if (event.getType() == AiEvent.Type.NARRATION) {
            return previous.getType() == ChatMessage.Type.NARRATION;
        }
        return event.getType() == AiEvent.Type.SYSTEM_EVENT
                && previous.getType() == ChatMessage.Type.SYSTEM_EVENT;
    }

    /** 同批次内两个事件是否属于同一“流”（同类型；角色台词还需同一角色）。 */
    private static boolean sameStream(AiEvent first, AiEvent second) {
        if (first.getType() == AiEvent.Type.CHARACTER_TURN) {
            return second.getType() == AiEvent.Type.CHARACTER_TURN
                    && java.util.Objects.equals(first.getCharacterId(), second.getCharacterId());
        }
        return first.getType() == second.getType();
    }

    private static String normalize(String value) {
        StringBuilder normalized = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c) || Character.UnicodeScript.of(c)
                    == Character.UnicodeScript.HAN) {
                normalized.append(Character.toLowerCase(c));
            }
        }
        return normalized.toString();
    }

    /** 归一化文本的编辑距离相似度（入参需已归一化）。 */
    private static double similarityNormalized(String left, String right) {
        if (left.isEmpty() || right.isEmpty()) {
            return left.equals(right) ? 1d : 0d;
        }
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int substitution = previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1), substitution);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return 1d - ((double) previous[right.length()] / Math.max(left.length(), right.length()));
    }
}
