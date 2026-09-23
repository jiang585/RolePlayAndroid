package com.example.roleplaychat.domain.ai;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * 标准结构化输出模型（架构文档 §8.3/§8.4）。
 */
public final class StructuredOutput {

    private StructuredOutput() {
    }

    public static final class Root {
        @SerializedName("schema_version")
        public int schemaVersion;
        @SerializedName("request_id")
        public String requestId;
        @SerializedName("events")
        public List<Event> events = new ArrayList<>();
        @SerializedName("continue_scene")
        public boolean continueScene;
        @SerializedName("await_player")
        public boolean awaitPlayer;
        @SerializedName("moments")
        public List<MomentAction> moments = new ArrayList<>();
        @SerializedName("image_actions")
        public List<ImageAction> imageActions = new ArrayList<>();
    }

    public static final class Event {
        @SerializedName("event_id")
        public String eventId;
        @SerializedName("type")
        public String type; // narration | character_turn | system_event
        @SerializedName("character_id")
        public String characterId;
        @SerializedName("content")
        public String content;
    }

    public static final class MomentAction {
        @SerializedName("type") public String type; // post | comment
        @SerializedName("character_id") public String characterId;
        @SerializedName("content") public String content;
        @SerializedName("moment_id") public String momentId;
        @SerializedName("parent_comment_id") public String parentCommentId;
    }

    public static final class ImageAction {
        @SerializedName("action_id") public String actionId;
        @SerializedName("message_id") public String messageId;
        @SerializedName("character_id") public String characterId;
        @SerializedName("intent") public String intent;
        @SerializedName("trigger") public String trigger;
        @SerializedName("scene") public String scene;
        @SerializedName("framing") public String framing;
        @SerializedName("mood") public String mood;
        @SerializedName("outfit") public String outfit;
        @SerializedName("contains_character") public Boolean containsCharacter;
    }
}
