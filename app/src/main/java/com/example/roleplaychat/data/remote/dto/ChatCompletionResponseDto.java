package com.example.roleplaychat.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * 非流式响应 DTO（用于测试连接等一次性请求）。
 */
public class ChatCompletionResponseDto {

    @SerializedName("id")
    public String id;

    @SerializedName("choices")
    public List<Choice> choices = new ArrayList<>();

    public static final class Choice {
        @SerializedName("index")
        public int index;
        @SerializedName("message")
        public Message message;
    }

    public static final class Message {
        @SerializedName("role")
        public String role;
        @SerializedName("content")
        public String content;
    }
}
