package com.example.roleplaychat.domain.repository;

import androidx.annotation.Nullable;

import com.example.roleplaychat.domain.model.AppErrorCode;
import com.example.roleplaychat.domain.model.PromptMessage;
import java.util.List;

/**
 * AI 仓库接口（架构文档 §7.1）。
 */
public interface AiRepository {

    /**
     * 发起流式对话请求。
     *
     * @param request  请求（含 requestId）
     * @param listener 流式回调
     * @return 可取消句柄
     */
    CancellableRequest streamChat(com.example.roleplaychat.domain.model.AiRequest request,
                                  AiStreamListener listener);

    /** 测试连接：使用最小请求验证认证/模型/网络，返回 null 表示成功。 */
    @Nullable
    AppErrorCode testConnection();

    /**
     * 按给定配置测试连接（不落盘、不影响当前启用的运行时配置）。
     *
     * @return null 表示成功
     */
    @Nullable
    AppErrorCode testConnection(com.example.roleplaychat.domain.model.ApiConfig config);

    CancellableRequest streamPrompt(String requestId, List<PromptMessage> messages,
                                    String model, int maxTokens, float temperature, float topP,
                                    AiStreamListener listener);
}
