package com.example.roleplaychat.domain.ai;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.roleplaychat.domain.model.AiRequest;
import com.example.roleplaychat.domain.model.ApiConfig;
import com.example.roleplaychat.domain.model.AppErrorCode;
import com.example.roleplaychat.domain.model.ChatMessage;
import com.example.roleplaychat.domain.model.PromptMessage;
import com.example.roleplaychat.domain.repository.AiRepository;
import com.example.roleplaychat.domain.repository.AiStreamListener;
import com.example.roleplaychat.domain.repository.CancellableRequest;
import com.example.roleplaychat.domain.repository.CharacterRepository;
import com.example.roleplaychat.domain.repository.ChatRepository;
import com.example.roleplaychat.domain.repository.ScriptRepository;
import com.example.roleplaychat.domain.repository.SettingsRepository;
import com.example.roleplaychat.domain.repository.WorldRepository;
import com.example.roleplaychat.util.IdGenerator;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AiTurnOrchestratorTest {

    private FakeAiRepository aiRepository;
    private AiTurnOrchestrator orchestrator;
    private ChatRepository chats;
    private SettingsRepository settings;

    @Before
    public void setUp() {
        ScriptRepository scripts = mock(ScriptRepository.class);
        WorldRepository worlds = mock(WorldRepository.class);
        CharacterRepository characters = mock(CharacterRepository.class);
        chats = mock(ChatRepository.class);
        settings = mock(SettingsRepository.class);
        when(characters.getEnabledByScriptId(anyString())).thenReturn(Collections.emptyList());
        when(chats.loadBefore(anyString(), anyLong(), anyInt())).thenReturn(Collections.emptyList());
        when(chats.loadAll(anyString())).thenReturn(Collections.emptyList());
        when(settings.getContextRecentCount()).thenReturn(20);
        when(settings.getApiConfig()).thenReturn(new ApiConfig(
                "https://example.test/", "key", "model", 0.7f, 1f, 512));
        aiRepository = new FakeAiRepository();
        AtomicInteger ids = new AtomicInteger();
        IdGenerator idGenerator = new IdGenerator() {
            @Override public String newId() { return "id-" + ids.incrementAndGet(); }
            @Override public String newRequestId() { return "request-" + ids.incrementAndGet(); }
        };
        orchestrator = new AiTurnOrchestrator(scripts, worlds, characters, chats, settings,
                aiRepository, idGenerator, "zh-CN");
    }

    @Test
    public void requestsFromDifferentScripts_doNotCancelEachOther() {
        String first = start("script-1");
        String second = start("script-2");

        assertFalse(aiRepository.handle(first).cancelled);
        assertFalse(aiRepository.handle(second).cancelled);
        assertTrue(orchestrator.isActive("script-1"));
        assertTrue(orchestrator.isActive("script-2"));
    }

    @Test
    public void replacementAndStop_areScopedToScript() {
        String oldFirst = start("script-1");
        String second = start("script-2");
        String newFirst = start("script-1");

        assertTrue(aiRepository.handle(oldFirst).cancelled);
        assertFalse(aiRepository.handle(newFirst).cancelled);
        assertFalse(aiRepository.handle(second).cancelled);

        orchestrator.stop("script-1", oldFirst);
        assertFalse(aiRepository.handle(newFirst).cancelled);
        assertTrue(orchestrator.isActive("script-1"));

        orchestrator.stop("script-2");

        assertTrue(aiRepository.handle(second).cancelled);
        assertFalse(aiRepository.handle(newFirst).cancelled);
        assertTrue(orchestrator.isActive("script-1"));
        assertFalse(orchestrator.isActive("script-2"));
    }

    @Test
    public void start_sendsLongTermMemoryAndRecentConversation() {
        ChatMessage older = message("old", 1, "早期承诺：保留通行证");
        ChatMessage latest = message("latest", 2, "现在出发");
        when(chats.loadAll("script-1")).thenReturn(java.util.Arrays.asList(older, latest));
        when(settings.getContextRecentCount()).thenReturn(1);

        String requestId = start("script-1");
        List<PromptMessage> messages = aiRepository.request(requestId).getMessages();

        assertTrue(messages.get(1).getContent().contains("早期承诺：保留通行证"));
        assertTrue(messages.get(1).getContent().contains("现在出发"));
    }

    private String start(String scriptId) {
        return orchestrator.start(scriptId, AiRequest.Mode.NORMAL_REPLY, 0, null,
                mock(AiTurnOrchestrator.Callback.class));
    }

    private ChatMessage message(String id, long sequence, String content) {
        return ChatMessage.builder().id(id).scriptId("script-1")
                .type(ChatMessage.Type.CHARACTER_TEXT).side(ChatMessage.Side.MINE)
                .senderDisplayName("玩家").content(content).sequence(sequence)
                .createdAt(sequence).build();
    }

    private static final class FakeAiRepository implements AiRepository {
        private final Map<String, Handle> handles = new HashMap<>();
        private final Map<String, AiRequest> requests = new HashMap<>();

        @Override
        public CancellableRequest streamChat(AiRequest request, AiStreamListener listener) {
            Handle handle = new Handle();
            handles.put(request.getRequestId(), handle);
            requests.put(request.getRequestId(), request);
            listener.onStarted(request.getRequestId());
            return handle;
        }

        @Override public AppErrorCode testConnection() { return null; }

        @Override
        public CancellableRequest streamPrompt(String requestId, List<PromptMessage> messages,
                String model, int maxTokens, float temperature, float topP,
                AiStreamListener listener) {
            throw new UnsupportedOperationException();
        }

        private Handle handle(String requestId) {
            return handles.get(requestId);
        }

        private AiRequest request(String requestId) {
            return requests.get(requestId);
        }
    }

    private static final class Handle implements CancellableRequest {
        private volatile boolean cancelled;

        @Override public void cancel() { cancelled = true; }
    }
}
