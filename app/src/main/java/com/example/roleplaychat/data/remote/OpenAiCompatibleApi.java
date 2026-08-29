package com.example.roleplaychat.data.remote;

import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

import com.example.roleplaychat.data.remote.dto.ChatCompletionResponseDto;

/**
 * OpenAI 兼容 API（架构文档 §8）。
 * 流式请求通过 OkHttp 原生 Call 发起（见 AiRepositoryImpl），
 * 本接口用于非流式（测试连接）。
 */
public interface OpenAiCompatibleApi {

    @POST("chat/completions")
    Call<ChatCompletionResponseDto> createChatCompletion(@Body RequestBody body);
}
