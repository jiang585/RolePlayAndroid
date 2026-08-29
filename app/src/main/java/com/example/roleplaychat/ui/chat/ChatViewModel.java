package com.example.roleplaychat.ui.chat;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.example.roleplaychat.domain.model.AiBatch;
import com.example.roleplaychat.domain.model.AppErrorCode;
import com.example.roleplaychat.domain.model.Appearance;
import com.example.roleplaychat.domain.model.ChatMessage;
import com.example.roleplaychat.domain.model.PlayerIdentity;
import com.example.roleplaychat.domain.model.Script;
import com.example.roleplaychat.domain.repository.AppearanceRepository;
import com.example.roleplaychat.domain.repository.CharacterRepository;
import com.example.roleplaychat.domain.repository.ChatRepository;
import com.example.roleplaychat.domain.repository.ScriptRepository;
import com.example.roleplaychat.domain.usecase.AdvanceAiUseCase;
import com.example.roleplaychat.domain.usecase.SendPlayerMessageUseCase;
import com.example.roleplaychat.domain.usecase.StopGenerationUseCase;
import com.example.roleplaychat.ui.common.SingleEvent;
import com.example.roleplaychat.util.AppExecutors;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 聊天 ViewModel（架构文档 §10.2/§10.3）。
 * 职责：观察消息/身份/装扮、发送消息、AI 推进/停止、分页加载。
 * 所有同步 DB 调用在后台线程，LiveData 通过 postValue 更新（线程安全）。
 */
public class ChatViewModel extends ViewModel {

    private static final int PAGE_SIZE = 50;
    private static final int OBSERVE_LIMIT = 200;
    private static final long NO_PENDING_PLAYER_SEND = 0L;
    private static final long ACQUIRING_PLAYER_SEND = -1L;

    private final ScriptRepository scriptRepository;
    private final CharacterRepository characterRepository;
    private final ChatRepository chatRepository;
    private final AppearanceRepository appearanceRepository;
    private final SendPlayerMessageUseCase sendPlayerMessageUseCase;
    private final AdvanceAiUseCase advanceAiUseCase;
    private final StopGenerationUseCase stopGenerationUseCase;
    private final AppExecutors executors;

    private final MutableLiveData<ChatUiState> uiState = new MutableLiveData<>(
            ChatUiState.builder().build());
    private final MutableLiveData<SingleEvent<String>> events = new MutableLiveData<>();

    private String scriptId;
    private List<ChatMessage> fullHistory = new ArrayList<>();
    private List<ChatMessage> earlierHistory = new ArrayList<>();
    private boolean historyLoaded;
    private String draft = "";
    private LiveData<Script> scriptSource;
    private LiveData<List<ChatMessage>> messageSource;
    private LiveData<PlayerIdentity> identitySource;
    private LiveData<Appearance> appearanceSource;
    private final Observer<Script> scriptObserver = this::onScriptChanged;
    private final Observer<List<ChatMessage>> messageObserver = this::onMessagesChanged;
    private final Observer<PlayerIdentity> identityObserver = this::onIdentityChanged;
    private final Observer<Appearance> appearanceObserver = this::onAppearanceChanged;
    private PlayerIdentity identity;
    private Appearance appearance;
    private volatile long sendGeneration;
    // Occupies the interval before the player-message callback marks the UI as generating.
    private final AtomicLong pendingPlayerSendGeneration =
            new AtomicLong(NO_PENDING_PLAYER_SEND);

    public ChatViewModel(ScriptRepository scriptRepository,
                         CharacterRepository characterRepository,
                         ChatRepository chatRepository,
                         AppearanceRepository appearanceRepository,
                         SendPlayerMessageUseCase sendPlayerMessageUseCase,
                         AdvanceAiUseCase advanceAiUseCase,
                         StopGenerationUseCase stopGenerationUseCase,
                         AppExecutors executors) {
        this.scriptRepository = scriptRepository;
        this.characterRepository = characterRepository;
        this.chatRepository = chatRepository;
        this.appearanceRepository = appearanceRepository;
        this.sendPlayerMessageUseCase = sendPlayerMessageUseCase;
        this.advanceAiUseCase = advanceAiUseCase;
        this.stopGenerationUseCase = stopGenerationUseCase;
        this.executors = executors;
    }

    public void setScriptId(String scriptId) {
        if (java.util.Objects.equals(this.scriptId, scriptId)) {
            return;
        }
        detachSources();
        // Old asynchronous send callbacks must not mutate the newly selected script's UI.
        sendGeneration++;
        pendingPlayerSendGeneration.set(NO_PENDING_PLAYER_SEND);
        this.scriptId = scriptId;
        earlierHistory = new ArrayList<>();
        fullHistory = new ArrayList<>();
        historyLoaded = false;
        identity = null;
        appearance = null;
        if (scriptId != null && !scriptId.trim().isEmpty()) {
            observe();
        }
    }

    public LiveData<ChatUiState> getUiState() {
        return uiState;
    }

    public LiveData<SingleEvent<String>> getEvents() {
        return events;
    }

    private void observe() {
        scriptSource = scriptRepository.observeById(scriptId);
        messageSource = chatRepository.observeLatest(scriptId, OBSERVE_LIMIT);
        identitySource = scriptRepository.observePlayerIdentity(scriptId);
        appearanceSource = appearanceRepository.observeEffective(scriptId, null);
        scriptSource.observeForever(scriptObserver);
        messageSource.observeForever(messageObserver);
        identitySource.observeForever(identityObserver);
        appearanceSource.observeForever(appearanceObserver);
    }

    private void detachSources() {
        if (scriptSource != null) {
            scriptSource.removeObserver(scriptObserver);
            scriptSource = null;
        }
        if (messageSource != null) {
            messageSource.removeObserver(messageObserver);
            messageSource = null;
        }
        if (identitySource != null) {
            identitySource.removeObserver(identityObserver);
            identitySource = null;
        }
        if (appearanceSource != null) {
            appearanceSource.removeObserver(appearanceObserver);
            appearanceSource = null;
        }
    }

    @Override
    protected void onCleared() {
        detachSources();
        super.onCleared();
    }

    private void onScriptChanged(Script script) {
        // 仅用于确认剧本存在；无需渲染
    }

    private void onMessagesChanged(List<ChatMessage> messages) {
        List<ChatMessage> latest = messages == null ? new ArrayList<>() : messages;
        if (latest.isEmpty()) {
            earlierHistory = new ArrayList<>();
            fullHistory = new ArrayList<>();
        } else {
            long firstLatestSequence = latest.get(0).getSequence();
            List<ChatMessage> merged = new ArrayList<>();
            for (ChatMessage message : earlierHistory) {
                if (message.getSequence() < firstLatestSequence) {
                    merged.add(message);
                }
            }
            merged.addAll(latest);
            fullHistory = merged;
        }
        historyLoaded = true;
        emit();
    }

    private void onIdentityChanged(PlayerIdentity identity) {
        this.identity = identity;
        emit();
    }

    private void onAppearanceChanged(Appearance appearance) {
        this.appearance = appearance;
        emit();
    }

    private void emit() {
        ChatUiState current = uiState.getValue();
        uiState.postValue(ChatUiState.builder()
                .initialLoading(!historyLoaded)
                .loadingEarlier(current != null && current.isLoadingEarlier())
                .generating(current != null && current.isGenerating())
                .activeRequestId(current == null ? null : current.getActiveRequestId())
                .items(buildItems(fullHistory))
                .identity(identity)
                .draft(draft)
                .appearance(appearance)
                .error(current == null ? null : current.getError())
                .build());
    }

    private List<ChatListItem> buildItems(List<ChatMessage> messages) {
        List<ChatListItem> items = new ArrayList<>();
        String lastDate = null;
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        for (ChatMessage message : messages) {
            String date = fmt.format(new Date(message.getCreatedAt()));
            if (!date.equals(lastDate)) {
                items.add(ChatListItem.dateSeparator(date));
                lastDate = date;
            }
            items.add(ChatListItem.message(message));
        }
        return items;
    }

    // ---------- 用户动作 ----------

    public void setDraft(String text) {
        this.draft = text == null ? "" : text;
    }

    public void sendMessage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        final String targetScriptId = scriptId;
        if (targetScriptId == null || targetScriptId.trim().isEmpty()) {
            return;
        }
        // 一次玩家发送只允许对应一个 AI 请求。新发送会使旧回调失效。
        ChatUiState current = uiState.getValue();
        if (current != null && current.isGenerating()) {
            stopGeneration();
        }
        if (!pendingPlayerSendGeneration.compareAndSet(
                NO_PENDING_PLAYER_SEND, ACQUIRING_PLAYER_SEND)) {
            return;
        }
        final long generation = ++sendGeneration;
        pendingPlayerSendGeneration.set(generation);
        executors.diskIO().execute(() -> {
            PlayerIdentity identity = scriptRepository.getPlayerIdentity(targetScriptId);
            if (identity == null) {
                releasePendingPlayerSend(generation);
                events.postValue(new SingleEvent<>("error:no_identity"));
                return;
            }
            com.example.roleplaychat.domain.model.AppError error =
                    sendPlayerMessageUseCase.execute(identity, text.trim(), System.currentTimeMillis(),
                    new SendPlayerMessageUseCase.Callback() {
                        @Override
                        public void onPlayerMessageSaved(ChatMessage message) {
                            if (!isCurrentSend(targetScriptId, generation)) {
                                return;
                            }
                            releasePendingPlayerSend(generation);
                            draft = "";
                            setGenerating(true, null);
                        }

                        @Override
                        public void onGenerationStarted(String requestId) {
                            if (!isCurrentSend(targetScriptId, generation)) {
                                return;
                            }
                            setGenerating(true, requestId);
                        }

                        @Override
                        public void onGenerationFailed(String requestId, AppErrorCode errorCode) {
                            if (!isCurrentSend(targetScriptId, generation)) {
                                return;
                            }
                            releasePendingPlayerSend(generation);
                            setGenerating(false, null);
                            if (errorCode != null && errorCode != AppErrorCode.CANCELLED_BY_USER) {
                                events.postValue(new SingleEvent<>("error:" + errorCode.getCode()));
                            }
                        }

                        @Override
                        public void onGenerationFinished(String requestId, boolean continueScene) {
                            if (generation == sendGeneration) {
                                if (continueScene) {
                                    startAutomaticContinuation(generation);
                                } else {
                                    setGenerating(false, null);
                                }
                            }
                        }
                    });
            if (error != null) {
                releasePendingPlayerSend(generation);
                events.postValue(new SingleEvent<>("error:" + error.getCode().getCode()));
            }
        });
    }

    public void sendNarration(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        stopGeneration();
        final long generation = ++sendGeneration;
        executors.diskIO().execute(() -> {
            PlayerIdentity identity = scriptRepository.getPlayerIdentity(scriptId);
            if (identity == null) {
                events.postValue(new SingleEvent<>("error:no_identity"));
                return;
            }
            // 旁白消息入库（居中、无角色、无气泡）
            chatRepository.insertNarration(scriptId, text.trim(), System.currentTimeMillis());
            // 触发一次 AI 编排以回应旁白
            sendPlayerMessageUseCase.triggerOrchestration(identity,
                    new SendPlayerMessageUseCase.Callback() {
                        @Override
                        public void onPlayerMessageSaved(ChatMessage message) {
                        }

                        @Override
                        public void onGenerationStarted(String requestId) {
                            if (generation != sendGeneration) {
                                return;
                            }
                            setGenerating(true, requestId);
                        }

                        @Override
                        public void onGenerationFailed(String requestId, AppErrorCode errorCode) {
                            if (generation != sendGeneration) {
                                return;
                            }
                            setGenerating(false, null);
                        }

                        @Override
                        public void onGenerationFinished(String requestId, boolean continueScene) {
                            if (generation == sendGeneration) {
                                if (continueScene) {
                                    startAutomaticContinuation(generation);
                                } else {
                                    setGenerating(false, null);
                                }
                            }
                        }
                    });
        });
    }

    private void startAutomaticContinuation(long generation) {
        ChatUiState current = uiState.getValue();
        uiState.postValue(ChatUiState.builder()
                .initialLoading(false)
                .loadingEarlier(current != null && current.isLoadingEarlier())
                .generating(true)
                .activeRequestId("advance")
                .items(current == null ? new ArrayList<>() : current.getItems())
                .identity(current == null ? null : current.getIdentity())
                .draft(draft)
                .appearance(current == null ? null : current.getAppearance())
                .error(null)
                .build());
        boolean started = advanceAiUseCase.startContinuous(scriptId, new AdvanceAiUseCase.Callback() {
            @Override
            public void onRoundStarted(int round, String requestId) {
                if (generation == sendGeneration) {
                    setGenerating(true, requestId);
                }
            }

            @Override
            public void onRoundCommitted(int round, String requestId, AiBatch batch) {
                // 消息入库后 LiveData 自动刷新
            }

            @Override
            public void onFinished(int completedRounds) {
                if (generation == sendGeneration) {
                    setGenerating(false, null);
                }
            }

            @Override
            public void onFailed(int round, AppErrorCode errorCode) {
                if (generation != sendGeneration) {
                    return;
                }
                setGenerating(false, null);
                if (errorCode != null && errorCode != AppErrorCode.CANCELLED_BY_USER) {
                    events.postValue(new SingleEvent<>("error:" + errorCode.getCode()));
                }
            }
        });
        if (!started) {
            setGenerating(false, null);
            events.postValue(new SingleEvent<>("error:" + AppErrorCode.UNKNOWN.getCode()));
        }
    }

    public void stopGeneration() {
        sendGeneration++;
        pendingPlayerSendGeneration.set(NO_PENDING_PLAYER_SEND);
        stopGenerationUseCase.execute(scriptId);
        setGenerating(false, null);
    }

    private void releasePendingPlayerSend(long generation) {
        pendingPlayerSendGeneration.compareAndSet(generation, NO_PENDING_PLAYER_SEND);
    }

    private boolean isCurrentSend(String targetScriptId, long generation) {
        return generation == sendGeneration && java.util.Objects.equals(targetScriptId, scriptId);
    }

    public void loadEarlier() {
        ChatUiState current = uiState.getValue();
        if (fullHistory.isEmpty() || (current != null && current.isLoadingEarlier())) {
            return;
        }
        setLoadingEarlier(true);
        long earliest = fullHistory.get(0).getSequence();
        executors.diskIO().execute(() -> {
            List<ChatMessage> earlier = chatRepository.loadBefore(scriptId, earliest, PAGE_SIZE);
            if (!earlier.isEmpty()) {
                List<ChatMessage> mergedEarlier = new ArrayList<>(earlier);
                mergedEarlier.addAll(earlierHistory);
                earlierHistory = mergedEarlier;
                List<ChatMessage> merged = new ArrayList<>(earlier);
                merged.addAll(fullHistory);
                fullHistory = merged;
            }
            executors.mainThread().execute(() -> setLoadingEarlier(false));
        });
    }

    public void clearChat() {
        executors.diskIO().execute(() -> chatRepository.clearMessages(scriptId));
    }

    private void setGenerating(boolean generating, String requestId) {
        ChatUiState current = uiState.getValue();
        if (current == null) {
            return;
        }
        uiState.postValue(ChatUiState.builder()
                .initialLoading(current.isInitialLoading())
                .loadingEarlier(current.isLoadingEarlier())
                .generating(generating)
                .activeRequestId(requestId)
                .items(current.getItems())
                .identity(current.getIdentity())
                .draft(draft)
                .appearance(current.getAppearance())
                .error(current.getError())
                .build());
    }

    private void setLoadingEarlier(boolean loading) {
        ChatUiState current = uiState.getValue();
        if (current == null) {
            return;
        }
        uiState.postValue(ChatUiState.builder()
                .initialLoading(current.isInitialLoading())
                .loadingEarlier(loading)
                .generating(current.isGenerating())
                .activeRequestId(current.getActiveRequestId())
                .items(buildItems(fullHistory))
                .identity(identity)
                .draft(draft)
                .appearance(appearance)
                .error(current.getError())
                .build());
    }
}
