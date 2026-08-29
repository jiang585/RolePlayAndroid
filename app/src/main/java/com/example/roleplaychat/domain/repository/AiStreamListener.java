package com.example.roleplaychat.domain.repository;

import androidx.annotation.Nullable;

import com.example.roleplaychat.domain.model.AppErrorCode;

/**
 * AI 流式回调（架构文档 §7.1）：必须映射为领域事件，UI 不感知 Retrofit/OkHttp 类型。
 */
public interface AiStreamListener {

    /** 请求已开始。 */
    void onStarted(String requestId);

    /** 文本增量（预览用，节流由上层控制）。 */
    void onTextDelta(String requestId, String delta);

    /** 完整响应已收到（包含最终文本）。 */
    void onCompleted(String requestId, String fullText);

    /** 请求失败。 */
    void onFailed(String requestId, @Nullable AppErrorCode errorCode, @Nullable String message);
}
