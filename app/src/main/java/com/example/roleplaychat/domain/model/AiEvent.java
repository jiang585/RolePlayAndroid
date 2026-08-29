package com.example.roleplaychat.domain.model;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * AI 输出事件（架构文档 §8.3 标准结构化输出中的单个事件）。
 */
public final class AiEvent {

    public enum Type {
        /** 旁白/动作/表情，渲染为居中系统提示。 */
        NARRATION,
        /** 某角色发言。 */
        CHARACTER_TURN,
        /** 系统事件（公告等）。 */
        SYSTEM_EVENT
    }

    private final String eventId;
    private final Type type;
    @Nullable
    private final String characterId;
    private final String content;
    private final int turnIndex;

    public AiEvent(String eventId, Type type, @Nullable String characterId, String content, int turnIndex) {
        this.eventId = Objects.requireNonNull(eventId);
        this.type = Objects.requireNonNull(type);
        this.characterId = characterId;
        this.content = Objects.requireNonNull(content);
        this.turnIndex = turnIndex;
    }

    public String getEventId() {
        return eventId;
    }

    public Type getType() {
        return type;
    }

    @Nullable
    public String getCharacterId() {
        return characterId;
    }

    public String getContent() {
        return content;
    }

    public int getTurnIndex() {
        return turnIndex;
    }
}
