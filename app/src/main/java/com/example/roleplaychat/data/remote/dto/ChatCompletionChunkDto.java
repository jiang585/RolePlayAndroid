package com.example.roleplaychat.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * SSE 流式分块 DTO（架构文档 §8.5）。
 */
public class ChatCompletionChunkDto {

    @SerializedName("id")
    public String id;

    @SerializedName("choices")
    public List<Choice> choices = new ArrayList<>();

    public static final class Choice {
        @SerializedName("index")
        public int index;
        @SerializedName("delta")
        public Delta delta;
        @SerializedName("finish_reason")
        public String finishReason;
    }

    public static final class Delta {
        @SerializedName("role")
        public String role;
        @SerializedName("content")
        public String content;
    }
}
