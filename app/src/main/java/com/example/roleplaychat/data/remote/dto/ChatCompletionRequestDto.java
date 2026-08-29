package com.example.roleplaychat.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 兼容 ChatCompletion 请求体（架构文档 §8）。
 */
public class ChatCompletionRequestDto {

    @SerializedName("model")
    public String model;

    @SerializedName("messages")
    public List<Message> messages = new ArrayList<>();

    @SerializedName("temperature")
    public float temperature;

    @SerializedName("top_p")
    public float topP;

    @SerializedName("max_tokens")
    public Integer maxTokens;

    @SerializedName("stream")
    public boolean stream;

    @SerializedName("response_format")
    public ResponseFormat responseFormat;

    @SerializedName("thinking")
    public Thinking thinking;

    public static final class Thinking {
        @SerializedName("type")
        public String type;

        public Thinking(String type) {
            this.type = type;
        }
    }

    public static final class ResponseFormat {
        @SerializedName("type")
        public String type;

        public ResponseFormat(String type) {
            this.type = type;
        }
    }

    public static final class Message {
        @SerializedName("role")
        public String role;
        @SerializedName("content")
        public String content;

        public Message() {
        }

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
