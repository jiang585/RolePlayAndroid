package com.example.roleplaychat.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.roleplaychat.data.remote.OpenAiServiceFactory;
import com.example.roleplaychat.domain.model.ApiConfig;
import com.example.roleplaychat.domain.model.PromptMessage;
import com.example.roleplaychat.domain.repository.AiStreamListener;

import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * AI 仓库请求构造测试：OpenCode Go（opencode.ai/docs/go）是 OpenAI 兼容端点，
 * 请求不得携带 DeepSeek 官方私有的 thinking 字段。
 */
public class AiRepositoryImplTest {

    @Test
    public void openCodeGo_streamPrompt_buildsOpenAiCompatibleBodyWithoutThinking() {
        ApiConfig config = new ApiConfig(ApiConfig.Provider.OPENCODE_GO,
                "https://opencode.ai/zen/go/v1", "sk-test", "deepseek-v4-flash",
                0.4f, 0.8f, 1024);
        OpenAiServiceFactory factory = mock(OpenAiServiceFactory.class);
        OkHttpClient client = mock(OkHttpClient.class);
        when(factory.baseUrl()).thenReturn("https://opencode.ai/zen/go/v1/");
        when(factory.model()).thenReturn("deepseek-v4-flash");
        when(factory.currentConfig()).thenReturn(config);
        when(factory.okHttpClient()).thenReturn(client);
        AtomicReference<Request> captured = new AtomicReference<>();
        when(client.newCall(any(Request.class))).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return mock(Call.class);
        });
        AiStreamListener listener = mock(AiStreamListener.class);

        AiRepositoryImpl repo = new AiRepositoryImpl(factory);
        repo.streamPrompt("req-1",
                Collections.singletonList(new PromptMessage(PromptMessage.Role.USER, "ping")),
                "deepseek-v4-flash", 1024, 0.4f, 0.8f, listener);

        Request request = captured.get();
        assertEquals("https://opencode.ai/zen/go/v1/chat/completions",
                request.url().toString());

        String body = bodyOf(request);
        assertFalse("OpenCode Go 请求不得携带 DeepSeek 私有 thinking 字段",
                body.contains("\"thinking\""));
        assertTrue("OpenAI 兼容 JSON 输出仍应保留 response_format",
                body.contains("\"response_format\""));
        assertTrue(body.contains("\"model\":\"deepseek-v4-flash\""));
        assertTrue(body.contains("\"stream\":true"));
        assertTrue(body.contains("\"role\":\"user\""));
    }

    @Test
    public void deepSeekNative_streamPrompt_keepsThinkingField() {
        ApiConfig config = new ApiConfig(ApiConfig.Provider.DEEPSEEK,
                "https://api.deepseek.com", "sk-test", "deepseek-v4-flash",
                0.4f, 0.8f, 1024);
        OpenAiServiceFactory factory = mock(OpenAiServiceFactory.class);
        OkHttpClient client = mock(OkHttpClient.class);
        when(factory.baseUrl()).thenReturn("https://api.deepseek.com/");
        when(factory.model()).thenReturn("deepseek-v4-flash");
        when(factory.currentConfig()).thenReturn(config);
        when(factory.okHttpClient()).thenReturn(client);
        AtomicReference<Request> captured = new AtomicReference<>();
        when(client.newCall(any(Request.class))).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return mock(Call.class);
        });
        AiStreamListener listener = mock(AiStreamListener.class);

        AiRepositoryImpl repo = new AiRepositoryImpl(factory);
        repo.streamPrompt("req-2",
                Collections.singletonList(new PromptMessage(PromptMessage.Role.USER, "ping")),
                "deepseek-v4-flash", 1024, 0.4f, 0.8f, listener);

        String body = bodyOf(captured.get());
        assertTrue("DeepSeek 官方 API 应保留 thinking 字段",
                body.contains("\"thinking\""));
        assertTrue(body.contains("\"type\":\"disabled\""));
    }

    private static String bodyOf(Request request) {
        okhttp3.RequestBody reqBody = request.body();
        okio.Buffer buffer = new okio.Buffer();
        try {
            reqBody.writeTo(buffer);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        return buffer.readUtf8();
    }
}