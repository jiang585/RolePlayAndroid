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
}
