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
    private final List<AiMomentAction> momentActions;
    private final List<AiImageAction> imageActions;
    private final boolean continueScene;
    private final boolean awaitPlayer;

    public AiBatch(String requestId, String scriptId, List<AiEvent> events) {
        this(requestId, scriptId, events, false);
    }

    public AiBatch(String requestId, String scriptId, List<AiEvent> events,
                   boolean continueScene) {
        this(requestId, scriptId, events, continueScene, null);
    }

    public AiBatch(String requestId, String scriptId, List<AiEvent> events,
                   boolean continueScene, List<AiMomentAction> momentActions) {
        this(requestId, scriptId, events, continueScene, false, momentActions);
    }

    public AiBatch(String requestId, String scriptId, List<AiEvent> events,
                   boolean continueScene, boolean awaitPlayer, List<AiMomentAction> momentActions) {
        this(requestId, scriptId, events, continueScene, awaitPlayer, momentActions,
                java.util.Collections.emptyList());
    }

    public AiBatch(String requestId, String scriptId, List<AiEvent> events,
                   boolean continueScene, boolean awaitPlayer, List<AiMomentAction> momentActions,
                   List<AiImageAction> imageActions) {
        this.requestId = Objects.requireNonNull(requestId);
        this.scriptId = Objects.requireNonNull(scriptId);
        this.events = events == null ? new ArrayList<>() : new ArrayList<>(events);
        this.momentActions = momentActions == null ? new ArrayList<>() : new ArrayList<>(momentActions);
        this.imageActions = imageActions == null ? new ArrayList<>() : new ArrayList<>(imageActions);
        this.continueScene = continueScene;
        this.awaitPlayer = awaitPlayer;
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

    public List<AiMomentAction> getMomentActions() { return Collections.unmodifiableList(momentActions); }

    public List<AiImageAction> getImageActions() { return Collections.unmodifiableList(imageActions); }

    public boolean isEmpty() {
        // 朋友圈动作本身也是有效输出；不能把“只留评论”的回合直接丢弃。
        return events.isEmpty() && momentActions.isEmpty() && imageActions.isEmpty();
    }

    public boolean shouldContinueScene() {
        return continueScene && !awaitPlayer;
    }

    /** 模型明确把下一步交给玩家；本地续演必须立即停下。 */
    public boolean shouldAwaitPlayer() { return awaitPlayer; }
}
