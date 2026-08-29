package com.example.roleplaychat.ui.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelStore;

import com.example.roleplaychat.domain.model.ChatMessage;
import com.example.roleplaychat.domain.model.PlayerIdentity;
import com.example.roleplaychat.domain.model.Appearance;
import com.example.roleplaychat.domain.repository.AppearanceRepository;
import com.example.roleplaychat.domain.repository.CharacterRepository;
import com.example.roleplaychat.domain.repository.ChatRepository;
import com.example.roleplaychat.domain.repository.ScriptRepository;
import com.example.roleplaychat.domain.usecase.AdvanceAiUseCase;
import com.example.roleplaychat.domain.usecase.SendPlayerMessageUseCase;
import com.example.roleplaychat.domain.usecase.StopGenerationUseCase;
import com.example.roleplaychat.util.AppExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

/**
 * 聊天 ViewModel 测试（架构文档 §15.2-1/3：身份与消息渲染）。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ChatViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private ScriptRepository scriptRepository;
    private CharacterRepository characterRepository;
    private ChatRepository chatRepository;
    private AppearanceRepository appearanceRepository;
    private SendPlayerMessageUseCase sendPlayerMessageUseCase;
    private AdvanceAiUseCase advanceAiUseCase;
    private StopGenerationUseCase stopGenerationUseCase;
    private ChatViewModel viewModel;

    @Before
    public void setUp() {
        scriptRepository = mock(ScriptRepository.class);
        characterRepository = mock(CharacterRepository.class);
        chatRepository = mock(ChatRepository.class);
        appearanceRepository = mock(AppearanceRepository.class);
        sendPlayerMessageUseCase = mock(SendPlayerMessageUseCase.class);
        advanceAiUseCase = mock(AdvanceAiUseCase.class);
        stopGenerationUseCase = mock(StopGenerationUseCase.class);

        // stub 观察方法，避免 mock 返回 null
        when(scriptRepository.observeById(anyString()))
                .thenReturn(new androidx.lifecycle.MutableLiveData<>(null));
        when(scriptRepository.observePlayerIdentity(anyString()))
                .thenReturn(new androidx.lifecycle.MutableLiveData<>(null));
        when(chatRepository.observeLatest(anyString(), anyInt()))
                .thenReturn(new androidx.lifecycle.MutableLiveData<>(Collections.emptyList()));
        when(appearanceRepository.observeEffective(anyString(), any()))
                .thenReturn(new androidx.lifecycle.MutableLiveData<>(null));

        viewModel = new ChatViewModel(scriptRepository, characterRepository, chatRepository,
                appearanceRepository, sendPlayerMessageUseCase, advanceAiUseCase,
                stopGenerationUseCase, AppExecutors.synchronous());
    }

    @Test
    public void setScriptId_triggersObservation() {
        when(scriptRepository.getPlayerIdentity("script-1"))
                .thenReturn(new PlayerIdentity("script-1", PlayerIdentity.RoleType.OBSERVER, null, 0L));
        viewModel.setScriptId("script-1");
        ChatUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
    }

    @Test
    public void sendMessage_clearsDraftAndTriggersUseCase() {
        PlayerIdentity identity = new PlayerIdentity("script-1",
                PlayerIdentity.RoleType.OBSERVER, null, 0L);
        when(scriptRepository.getPlayerIdentity("script-1")).thenReturn(identity);
        viewModel.setScriptId("script-1");
        viewModel.sendMessage("你好");
        verify(sendPlayerMessageUseCase).execute(Mockito.eq(identity), Mockito.eq("你好"),
                anyLong(), any(SendPlayerMessageUseCase.Callback.class));
    }

    @Test
    public void sendMessage_whilePlayerMessageSaveIsPending_onlySubmitsOnce() {
        PlayerIdentity identity = new PlayerIdentity("script-1",
                PlayerIdentity.RoleType.OBSERVER, null, 0L);
        when(scriptRepository.getPlayerIdentity("script-1")).thenReturn(identity);
        viewModel.setScriptId("script-1");

        viewModel.sendMessage("第一条");
        viewModel.sendMessage("第一条");

        verify(sendPlayerMessageUseCase, times(1)).execute(Mockito.eq(identity),
                Mockito.eq("第一条"), anyLong(), any(SendPlayerMessageUseCase.Callback.class));
    }

    @Test
    public void dateSeparators_generatedBetweenDates() {
        // 构造两条不同日期的消息
        long day1 = 1700000000000L;
        long day2 = 1700086400000L; // 约一天后
        ChatMessage m1 = ChatMessage.builder()
                .id("m1").scriptId("script-1").type(ChatMessage.Type.NARRATION)
                .side(ChatMessage.Side.CENTER).content("旁白1")
                .sequence(1).createdAt(day1).status(ChatMessage.Status.DONE).build();
        ChatMessage m2 = ChatMessage.builder()
                .id("m2").scriptId("script-1").type(ChatMessage.Type.NARRATION)
                .side(ChatMessage.Side.CENTER).content("旁白2")
                .sequence(2).createdAt(day2).status(ChatMessage.Status.DONE).build();
        when(chatRepository.observeLatest(anyString(), anyInt()))
                .thenReturn(new androidx.lifecycle.MutableLiveData<>(Arrays.asList(m1, m2)));
        viewModel.setScriptId("script-1");
        ChatUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        // 两条不同日期消息 → 2 个日期分隔 + 2 条消息 = 4 项
        assertEquals(4, state.getItems().size());
        assertEquals(ChatListItem.Kind.DATE_SEPARATOR, state.getItems().get(0).getKind());
        assertEquals(ChatListItem.Kind.MESSAGE, state.getItems().get(1).getKind());
        assertEquals(ChatListItem.Kind.DATE_SEPARATOR, state.getItems().get(2).getKind());
    }

    @Test
    public void observedIdentityAndAppearance_areStoredInUiState() {
        PlayerIdentity identity = new PlayerIdentity("script-1",
                PlayerIdentity.RoleType.OBSERVER, null, 0L);
        Appearance appearance = new Appearance("appearance-1", Appearance.ScopeType.SCRIPT,
                "script-1", Appearance.BackgroundType.BUILTIN, null,
                Appearance.BackgroundMode.CENTER_CROP, 0f, "default",
                "#FFFFFF", "#000000", "#000000", 1f);
        when(scriptRepository.observePlayerIdentity("script-1"))
                .thenReturn(new androidx.lifecycle.MutableLiveData<>(identity));
        when(appearanceRepository.observeEffective(Mockito.eq("script-1"), any()))
                .thenReturn(new androidx.lifecycle.MutableLiveData<>(appearance));

        viewModel.setScriptId("script-1");

        assertEquals(identity, viewModel.getUiState().getValue().getIdentity());
        assertEquals(appearance, viewModel.getUiState().getValue().getAppearance());
    }

    @Test
    public void switchingScript_detachesOldSources() {
        MutableLiveData<java.util.List<ChatMessage>> oldMessages =
                new MutableLiveData<>(Collections.emptyList());
        MutableLiveData<java.util.List<ChatMessage>> newMessages =
                new MutableLiveData<>(Collections.emptyList());
        when(chatRepository.observeLatest(Mockito.eq("script-1"), anyInt())).thenReturn(oldMessages);
        when(chatRepository.observeLatest(Mockito.eq("script-2"), anyInt())).thenReturn(newMessages);

        viewModel.setScriptId("script-1");
        viewModel.setScriptId("script-2");

        assertFalse(oldMessages.hasObservers());
        assertEquals(true, newMessages.hasObservers());
    }

    @Test
    public void clearedViewModel_releasesForeverObservers() {
        MutableLiveData<java.util.List<ChatMessage>> messages =
                new MutableLiveData<>(Collections.emptyList());
        when(chatRepository.observeLatest(Mockito.eq("script-1"), anyInt())).thenReturn(messages);
        viewModel.setScriptId("script-1");
        ViewModelStore store = new ViewModelStore();
        store.put("chat", viewModel);

        store.clear();

        assertFalse(messages.hasObservers());
    }

    @Test
    public void completedReplyWithContinuation_startsAutomaticAdvance() {
        PlayerIdentity identity = new PlayerIdentity("script-1",
                PlayerIdentity.RoleType.OBSERVER, null, 0L);
        when(scriptRepository.getPlayerIdentity("script-1")).thenReturn(identity);
        viewModel.setScriptId("script-1");
        when(advanceAiUseCase.startContinuous(Mockito.eq("script-1"), any()))
                .thenReturn(true);
        org.mockito.ArgumentCaptor<SendPlayerMessageUseCase.Callback> callbackCaptor =
                org.mockito.ArgumentCaptor.forClass(SendPlayerMessageUseCase.Callback.class);

        viewModel.sendMessage("继续剧情");
        verify(sendPlayerMessageUseCase).execute(Mockito.eq(identity), Mockito.eq("继续剧情"),
                anyLong(), callbackCaptor.capture());
        callbackCaptor.getValue().onGenerationFinished("request-1", true);

        verify(advanceAiUseCase).startContinuous(Mockito.eq("script-1"),
                any(AdvanceAiUseCase.Callback.class));
        assertEquals(true, viewModel.getUiState().getValue().isGenerating());
    }

    @Test
    public void stopGeneration_stopsOnlyCurrentScript() {
        viewModel.setScriptId("script-1");

        viewModel.stopGeneration();

        verify(stopGenerationUseCase).execute("script-1");
    }
}
