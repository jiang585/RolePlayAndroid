package com.example.roleplaychat.data.remote.sse;

import androidx.annotation.Nullable;

import com.example.roleplaychat.data.remote.dto.ChatCompletionChunkDto;
import com.example.roleplaychat.domain.model.AppErrorCode;
import com.example.roleplaychat.domain.repository.AiStreamListener;
import com.example.roleplaychat.util.JsonUtils;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * SSE 客户端（架构文档 §8.5）：
 * OkHttp 字节流 -> SseEventParser -> raw delta 缓冲 -> 预览回调 -> [DONE] -> 完成回调。
 * 取消通过 {@link Call#cancel()} 实现；所有回调携带统一 requestId。
 */
public final class SseClient {

    private final String requestId;
    private final Call call;
    private final AiStreamListener listener;
    private final StringBuilder rawBuffer = new StringBuilder();
    private final AtomicBoolean terminalDelivered = new AtomicBoolean(false);

    public SseClient(String requestId, Call call, AiStreamListener listener) {
        this.requestId = requestId;
        this.call = call;
        this.listener = listener;
    }

    public void start() {
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (call.isCanceled()) {
                    deliverFailure(AppErrorCode.CANCELLED_BY_USER, null);
                } else {
                    deliverFailure(AppErrorCode.NETWORK_UNAVAILABLE, e.getMessage());
                }
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    AppErrorCode code = mapHttpError(response.code());
                    deliverFailure(code, "HTTP " + response.code());
                    response.close();
                    return;
                }
                ResponseBody body = response.body();
                if (body == null) {
                    deliverFailure(AppErrorCode.OUTPUT_INVALID, "empty body");
                    response.close();
                    return;
                }
                SseEventParser parser = new SseEventParser();
                try (InputStream in = body.byteStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (call.isCanceled()) {
                            deliverFailure(AppErrorCode.CANCELLED_BY_USER, null);
                            return;
                        }
                        String event = parser.onLine(line);
                        if (event != null) {
                            handleEvent(event);
                        }
                        if (parser.isDone()) {
                            if (call.isCanceled()) {
                                deliverFailure(AppErrorCode.CANCELLED_BY_USER, null);
                            } else {
                                deliverCompleted();
                            }
                            return;
                        }
                    }
                    String last = parser.finish();
                    if (last != null) {
                        handleEvent(last);
                    }
                    if (call.isCanceled()) {
                        deliverFailure(AppErrorCode.CANCELLED_BY_USER, null);
                    } else {
                        deliverCompleted();
                    }
                } catch (IOException e) {
                    if (call.isCanceled()) {
                        deliverFailure(AppErrorCode.CANCELLED_BY_USER, null);
                    } else {
                        deliverFailure(AppErrorCode.NETWORK_UNAVAILABLE, e.getMessage());
                    }
                }
            }
        });
    }

    public void cancel() {
        call.cancel();
        deliverFailure(AppErrorCode.CANCELLED_BY_USER, null);
    }

    private void deliverCompleted() {
        if (terminalDelivered.compareAndSet(false, true)) {
            listener.onCompleted(requestId, rawBuffer.toString());
        }
    }

    private void deliverFailure(AppErrorCode errorCode, @Nullable String message) {
        if (terminalDelivered.compareAndSet(false, true)) {
            listener.onFailed(requestId, errorCode, message);
        }
    }

    private void handleEvent(String event) {
        if (event == null || event.isEmpty()) {
            return;
        }
        try {
            ChatCompletionChunkDto chunk = JsonUtils.fromJson(event, ChatCompletionChunkDto.class);
            if (chunk == null || chunk.choices == null || chunk.choices.isEmpty()) {
                return;
            }
            ChatCompletionChunkDto.Delta delta = chunk.choices.get(0).delta;
            if (delta != null && delta.content != null && !delta.content.isEmpty()) {
                rawBuffer.append(delta.content);
                listener.onTextDelta(requestId, delta.content);
            }
        } catch (JsonSyntaxException e) {
            // 忽略非 JSON 行（如注释或未知格式），不中断流
        }
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
