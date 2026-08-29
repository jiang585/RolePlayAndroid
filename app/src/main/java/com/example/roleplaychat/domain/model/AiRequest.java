package com.example.roleplaychat.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * AI 请求领域模型（架构文档 §8）。所有回调事件必须映射为领域事件，
 * UI 不感知 Retrofit/OkHttp 类型。
 */
public final class AiRequest {

    public enum Mode {
        /** 玩家发言后的普通回复编排。 */
        NORMAL_REPLY,
        /** AI 自动推进（多轮 NPC 自演）。 */
        AUTO_ADVANCE
    }

    private final String requestId;
    private final String scriptId;
    private final Mode mode;
    /** 自动推进时的轮次序号（普通回复为 0）。 */
    private final int roundIndex;
    private final List<PromptMessage> messages;
    private final String model;
    private final int maxTokens;
    private final float temperature;
    private final float topP;
    private final int maxEvents;

    public AiRequest(String requestId, String scriptId, Mode mode, int roundIndex,
                     List<PromptMessage> messages, String model, int maxTokens, float temperature,
                     float topP, int maxEvents) {
        this.requestId = Objects.requireNonNull(requestId);
        this.scriptId = Objects.requireNonNull(scriptId);
        this.mode = Objects.requireNonNull(mode);
        this.roundIndex = roundIndex;
        this.messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
        this.model = Objects.requireNonNull(model);
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.topP = topP;
        this.maxEvents = maxEvents;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getScriptId() {
        return scriptId;
    }

    public Mode getMode() {
        return mode;
    }

    public int getRoundIndex() {
        return roundIndex;
    }

    public List<PromptMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public String getModel() {
        return model;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public float getTemperature() {
        return temperature;
    }

    public float getTopP() {
        return topP;
    }

    public int getMaxEvents() {
        return maxEvents;
    }
}
