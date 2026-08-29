package com.example.roleplaychat.domain.usecase;

import androidx.annotation.Nullable;

import com.example.roleplaychat.domain.ai.AiTurnOrchestrator;
import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.AppErrorCode;
import com.example.roleplaychat.domain.model.ChatMessage;
import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.model.PlayerIdentity;
import com.example.roleplaychat.domain.repository.CharacterRepository;
import com.example.roleplaychat.domain.repository.ChatRepository;
import com.example.roleplaychat.domain.repository.ScriptRepository;
import com.example.roleplaychat.domain.repository.SettingsRepository;
import com.example.roleplaychat.domain.repository.WorldRepository;
import com.example.roleplaychat.util.AppExecutors;

/**
 * 发送玩家消息用例（架构文档 §8.6）：长度校验、快照入库、触发一次 AI 编排。
 * 单条消息上限 4096 Unicode 字符（§2.3）。
 * 所有 DB 与网络操作在后台线程执行，回调切回主线程（§3.2）。
 */
public class SendPlayerMessageUseCase {

    public static final int MAX_MESSAGE_LENGTH = 4096;

    public interface Callback {
        void onPlayerMessageSaved(ChatMessage message);

        void onGenerationStarted(String requestId);

        void onGenerationFailed(String requestId, @Nullable AppErrorCode errorCode);

        default void onGenerationFinished(String requestId, boolean continueScene) {
        }
    }

    private final ScriptRepository scriptRepository;
    private final CharacterRepository characterRepository;
    private final WorldRepository worldRepository;
    private final ChatRepository chatRepository;
    private final SettingsRepository settingsRepository;
    private final AiTurnOrchestrator orchestrator;
    private final AppExecutors executors;

    public SendPlayerMessageUseCase(ScriptRepository scriptRepository,
                                    CharacterRepository characterRepository,
                                    WorldRepository worldRepository,
                                    ChatRepository chatRepository,
                                    SettingsRepository settingsRepository,
                                    AiTurnOrchestrator orchestrator,
                                    AppExecutors executors) {
        this.scriptRepository = scriptRepository;
        this.characterRepository = characterRepository;
        this.worldRepository = worldRepository;
        this.chatRepository = chatRepository;
        this.settingsRepository = settingsRepository;
        this.orchestrator = orchestrator;
        this.executors = executors;
    }

    /**
     * @return 错误或 null 表示成功（消息已保存并触发编排）
     */
    @Nullable
    public AppError execute(PlayerIdentity identity, String content, long now, Callback callback) {
        if (identity == null || identity.getScriptId() == null) {
            return AppError.of(AppErrorCode.VALIDATION_FAILED, "identity required", false);
        }
        if (content == null || content.trim().isEmpty()) {
            return AppError.of(AppErrorCode.VALIDATION_FAILED, "message empty", false);
        }
        if (content.length() > MAX_MESSAGE_LENGTH) {
            return AppError.of(AppErrorCode.VALIDATION_FAILED, "message too long", false);
        }

        final String trimmed = content.trim();
        executors.diskIO().execute(() -> {
            // 快照：发送时角色昵称与头像（规则 2）
            String senderName = null;
            String avatarRef = null;
            if (!identity.isObserver() && identity.getCharacterId() != null) {
                CharacterProfile profile = characterRepository.getById(identity.getCharacterId());
                if (profile != null) {
                    senderName = profile.getName();
                    avatarRef = profile.getAvatarRef();
                }
            }

            final String finalSender = senderName;
            final String finalAvatar = avatarRef;
            ChatMessage saved = chatRepository.insertPlayerMessage(identity, trimmed,
                    finalSender, finalAvatar, null, now);
            scriptRepository.touchUpdatedAt(identity.getScriptId(), now);

            executors.mainThread().execute(() -> callback.onPlayerMessageSaved(saved));

            final String requestId = orchestrator.start(identity.getScriptId(),
                    com.example.roleplaychat.domain.model.AiRequest.Mode.NORMAL_REPLY, 0,
                    null, new AiTurnOrchestrator.Callback() {
                        @Override
                        public void onGenerationStarted(String requestId) {
                            executors.mainThread().execute(() -> callback.onGenerationStarted(requestId));
                        }

                        @Override
                        public void onBatchCommitted(String requestId,
                                                     com.example.roleplaychat.domain.model.AiBatch batch) {
                            executors.mainThread().execute(() -> callback.onGenerationFinished(
                                    requestId, batch.shouldContinueScene()));
                        }

                        @Override
                        public void onGenerationFailed(String requestId,
                                                       @Nullable AppErrorCode errorCode) {
                            executors.mainThread().execute(() -> callback.onGenerationFailed(requestId, errorCode));
                        }
                    });
        });
        return null;
    }

    /**
     * 仅触发一次 AI 编排（不插入玩家消息），用于旁白/动作消息已单独入库的场景。
     */
    public void triggerOrchestration(PlayerIdentity identity, Callback callback) {
        executors.diskIO().execute(() -> {
            final String requestId = orchestrator.start(identity.getScriptId(),
                    com.example.roleplaychat.domain.model.AiRequest.Mode.NORMAL_REPLY, 0,
                    null, new AiTurnOrchestrator.Callback() {
                        @Override
                        public void onGenerationStarted(String requestId) {
                            executors.mainThread().execute(() -> callback.onGenerationStarted(requestId));
                        }

                        @Override
                        public void onBatchCommitted(String requestId,
                                                     com.example.roleplaychat.domain.model.AiBatch batch) {
                            executors.mainThread().execute(() -> callback.onGenerationFinished(
                                    requestId, batch.shouldContinueScene()));
                        }

                        @Override
                        public void onGenerationFailed(String requestId,
                                                       @Nullable AppErrorCode errorCode) {
                            executors.mainThread().execute(() -> callback.onGenerationFailed(requestId, errorCode));
                        }
                    });
        });
    }
}
