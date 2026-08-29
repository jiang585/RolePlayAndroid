package com.example.roleplaychat.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * AI 输出批次（架构文档 §8.3）：一次请求产出的完整事件集合。
 */
public final class AiBatch {

    private final String requestId;
    private final String scriptId;
    private final List<AiEvent> events;
    private final boolean continueScene;

    public AiBatch(String requestId, String scriptId, List<AiEvent> events) {
        this(requestId, scriptId, events, false);
    }

    public AiBatch(String requestId, String scriptId, List<AiEvent> events,
                   boolean continueScene) {
        this.requestId = Objects.requireNonNull(requestId);
        this.scriptId = Objects.requireNonNull(scriptId);
        this.events = events == null ? new ArrayList<>() : new ArrayList<>(events);
        this.continueScene = continueScene;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getScriptId() {
        return scriptId;
    }

    public List<AiEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }

    public boolean shouldContinueScene() {
        return continueScene;
    }
}
