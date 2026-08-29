package com.example.roleplaychat.data.repository;

import com.example.roleplaychat.data.remote.OpenAiServiceFactory;
import com.example.roleplaychat.data.remote.dto.ChatCompletionRequestDto;
import com.example.roleplaychat.data.remote.dto.ChatCompletionResponseDto;
import com.example.roleplaychat.data.remote.sse.SseClient;
import com.example.roleplaychat.domain.model.AiRequest;
import com.example.roleplaychat.domain.model.AppErrorCode;
import com.example.roleplaychat.domain.model.PromptMessage;
import com.example.roleplaychat.domain.repository.AiRepository;
import com.example.roleplaychat.domain.repository.AiStreamListener;
import com.example.roleplaychat.domain.repository.CancellableRequest;
import com.example.roleplaychat.util.JsonUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import retrofit2.Callback;

/**
 * AI 仓库实现（架构文档 §8）：组装 OpenAI 兼容请求，SSE 流式接收，
 * 通过 {@link SseClient} 解析并转发为领域事件。
 */
public class AiRepositoryImpl implements AiRepository {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OpenAiServiceFactory serviceFactory;

    public AiRepositoryImpl(OpenAiServiceFactory serviceFactory) {
        this.serviceFactory = serviceFactory;
    }

    @Override
    public CancellableRequest streamChat(AiRequest request, AiStreamListener listener) {
        return streamPrompt(request.getRequestId(), request.getMessages(), request.getModel(),
                request.getMaxTokens(), request.getTemperature(), request.getTopP(), listener);
    }

    @Override
    public CancellableRequest streamPrompt(String requestId, List<PromptMessage> messages,
                                           String model, int maxTokens, float temperature,
                                           float topP, AiStreamListener listener) {
        ChatCompletionRequestDto dto = new ChatCompletionRequestDto();
        dto.model = model;
        dto.temperature = temperature;
        dto.topP = topP;
        dto.maxTokens = maxTokens;
        dto.stream = true;
        // DeepSeek JSON Output and OpenAI-compatible providers both support this form.
        // The system prompt still explicitly describes the application event schema.
        dto.responseFormat = new ChatCompletionRequestDto.ResponseFormat("json_object");
        if (isDeepSeekNative()) {
            // thinking 是 DeepSeek 官方 API 私有扩展，仅对 DeepSeek 本体发送；
            // OpenCode Go 等 OpenAI 兼容网关不识别该字段。
            dto.thinking = new ChatCompletionRequestDto.Thinking("disabled");
        }
        for (PromptMessage message : messages) {
            dto.messages.add(new ChatCompletionRequestDto.Message(
                    message.getRole().name().toLowerCase(Locale.ROOT), message.getContent()));
        }
        String json = JsonUtils.toJson(dto);
        Request httpRequest = new Request.Builder()
                .url(serviceFactory.baseUrl() + "chat/completions")
                .post(RequestBody.create(json, JSON))
                .build();
        Call call = serviceFactory.okHttpClient().newCall(httpRequest);
        SseClient sseClient = new SseClient(requestId, call, listener);
        listener.onStarted(requestId);
        sseClient.start();
        return sseClient::cancel;
    }

    @Override
    public AppErrorCode testConnection() {
        com.example.roleplaychat.domain.model.ApiConfig config = serviceFactory.currentConfig();
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            return AppErrorCode.AUTH_INVALID;
        }
        ChatCompletionRequestDto dto = new ChatCompletionRequestDto();
        dto.model = serviceFactoryDefaultModel();
        dto.messages.add(new ChatCompletionRequestDto.Message("user", "ping"));
        dto.temperature = 0.2f;
        dto.topP = 1.0f;
        dto.stream = false;
        dto.maxTokens = 8;
        if (isDeepSeekNative()) {
            dto.thinking = new ChatCompletionRequestDto.Thinking("disabled");
        }
        String json = JsonUtils.toJson(dto);
        Request httpRequest = new Request.Builder()
                .url(serviceFactory.baseUrl() + "chat/completions")
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response response = serviceFactory.okHttpClient().newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                return mapHttpError(response.code());
            }
            return null;
        } catch (IOException e) {
            return AppErrorCode.NETWORK_UNAVAILABLE;
        }
    }

    private String serviceFactoryDefaultModel() {
        return serviceFactory.model();
    }

    /** 是否直连 DeepSeek 官方 API（仅其识别 thinking 私有扩展）。 */
    private boolean isDeepSeekNative() {
        return serviceFactory.currentConfig() != null
                && serviceFactory.currentConfig().getProvider()
                == com.example.roleplaychat.domain.model.ApiConfig.Provider.DEEPSEEK;
    }

    private static AppErrorCode mapHttpError(int code) {
        switch (code) {
            case 401:
            case 403:
                return AppErrorCode.AUTH_INVALID;
            case 429:
                return AppErrorCode.RATE_LIMITED;
            case 404:
                return AppErrorCode.MODEL_NOT_FOUND;
            case 400:
            case 422:
                return AppErrorCode.OUTPUT_INVALID;
            case 408:
                return AppErrorCode.NETWORK_UNAVAILABLE;
            default:
                return AppErrorCode.NETWORK_UNAVAILABLE;
        }
    }
}
