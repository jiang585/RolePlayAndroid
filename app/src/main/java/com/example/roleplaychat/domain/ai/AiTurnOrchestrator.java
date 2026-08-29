package com.example.roleplaychat.domain.ai;

import androidx.annotation.Nullable;

import com.example.roleplaychat.domain.model.AiBatch;
import com.example.roleplaychat.domain.model.AiEvent;
import com.example.roleplaychat.domain.model.AiRequest;
import com.example.roleplaychat.domain.model.AppErrorCode;
import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.model.ChatMessage;
import com.example.roleplaychat.domain.model.PlayerIdentity;
import com.example.roleplaychat.domain.model.PromptMessage;
import com.example.roleplaychat.domain.model.WorldSetting;
import com.example.roleplaychat.domain.repository.AiRepository;
import com.example.roleplaychat.domain.repository.AiStreamListener;
import com.example.roleplaychat.domain.repository.CancellableRequest;
import com.example.roleplaychat.domain.repository.CharacterRepository;
import com.example.roleplaychat.domain.repository.ChatRepository;
import com.example.roleplaychat.domain.repository.ScriptRepository;
import com.example.roleplaychat.domain.repository.SettingsRepository;
import com.example.roleplaychat.domain.repository.WorldRepository;
import com.example.roleplaychat.util.IdGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 回合编排器（架构文档 §8.6/§8.7）：
 * 读取上下文 -> 组装 Prompt -> 流式请求 -> 完整解析校验 -> 批次入库。
 * 每个剧本最多一个 active generation（§12.2）。
 */
public final class AiTurnOrchestrator {

    public interface Callback {
        default void onGenerationStarted(String requestId) {
        }

        void onBatchCommitted(String requestId, AiBatch batch);

        void onGenerationFailed(String requestId, @Nullable AppErrorCode errorCode);
    }

    private final ScriptRepository scriptRepository;
    private final WorldRepository worldRepository;
    private final CharacterRepository characterRepository;
    private final ChatRepository chatRepository;
    private final SettingsRepository settingsRepository;
    private final AiRepository aiRepository;
    private final IdGenerator idGenerator;
    private final String language;

    private final Object requestLock = new Object();
    private final Map<String, ActiveRequest> activeRequests = new HashMap<>();

    private static final class ActiveRequest {
        private final String requestId;
        @Nullable
        private CancellableRequest request;

        private ActiveRequest(String requestId) {
            this.requestId = requestId;
        }
    }

    public AiTurnOrchestrator(ScriptRepository scriptRepository, WorldRepository worldRepository,
                              CharacterRepository characterRepository, ChatRepository chatRepository,
                              SettingsRepository settingsRepository, AiRepository aiRepository,
                              IdGenerator idGenerator, String language) {
        this.scriptRepository = scriptRepository;
        this.worldRepository = worldRepository;
        this.characterRepository = characterRepository;
        this.chatRepository = chatRepository;
        this.settingsRepository = settingsRepository;
        this.aiRepository = aiRepository;
        this.idGenerator = idGenerator;
        this.language = language;
    }

    /**
     * 开始一次 AI 编排（普通回复或自动推进单轮）。
     *
     * @param scriptId 剧本 ID
     * @param mode     请求模式
     * @param round    轮次（普通回复 0）
     * @param listener 流式监听（可为空，仅用于预览）
     * @param callback 完成/失败回调
     * @return 本次请求 ID
     */
    public String start(String scriptId, AiRequest.Mode mode, int round,
                        @Nullable AiStreamListener listener, Callback callback) {
        String requestId = idGenerator.newRequestId();
        ActiveRequest session = new ActiveRequest(requestId);

        ActiveRequest previous;
        synchronized (requestLock) {
            previous = activeRequests.put(scriptId, session);
        }
        if (previous != null && previous.request != null) {
            previous.request.cancel();
        }

        PlayerIdentity identity = scriptRepository.getPlayerIdentity(scriptId);
        WorldSetting world = worldRepository.getByScriptId(scriptId);
        List<CharacterProfile> enabledNpcs = characterRepository.getEnabledByScriptId(scriptId);

        // 玩家绑定角色后，该角色排除出可编排 NPC 列表（规则 3）
        List<CharacterProfile> npcPool = new ArrayList<>(enabledNpcs);
        if (identity != null && identity.getCharacterId() != null) {
            npcPool.removeIf(npc -> npc.getId().equals(identity.getCharacterId()));
        }

        CharacterProfile playerCharacter = null;
        if (identity != null && identity.getCharacterId() != null) {
            playerCharacter = characterRepository.getById(identity.getCharacterId());
        }

        List<ChatMessage> allMessages = chatRepository.loadAll(scriptId);
        int recentCount = settingsRepository.getContextRecentCount();
        int recentStart = Math.max(0, allMessages.size() - Math.max(1, recentCount));
        List<ChatMessage> recent = new ArrayList<>(allMessages.subList(recentStart, allMessages.size()));
        CharacterProfile mentionedCharacter = findMentionedCharacter(recent, npcPool);
        String conversation = ContextWindowPolicy.toPromptContext(allMessages, recentCount);

        AiContext context = new AiContext(scriptId, world, npcPool, identity, playerCharacter,
                conversation, language, 8, mentionedCharacter, mode == AiRequest.Mode.AUTO_ADVANCE);

        List<PromptMessage> messages = PromptAssembler.buildMessages(context, requestId);
        com.example.roleplaychat.domain.model.ApiConfig config = settingsRepository.getApiConfig();
        AiRequest request = new AiRequest(requestId, scriptId, mode, round, messages,
                config.getModel(), config.getMaxTokens(), config.getTemperature(),
                config.getTopP(), 8);

        AiStreamListener internal = new AiStreamListener() {
            @Override
            public void onStarted(String requestId) {
                if (listener != null && isCurrent(scriptId, session)) {
                    listener.onStarted(requestId);
                }
                if (isCurrent(scriptId, session)) {
                    callback.onGenerationStarted(requestId);
                }
            }

            @Override
            public void onTextDelta(String requestId, String delta) {
                if (listener != null && isCurrent(scriptId, session)) {
                    listener.onTextDelta(requestId, delta);
                }
            }

            @Override
            public void onCompleted(String requestId, String fullText) {
                if (!clearIfCurrent(scriptId, session)) {
                    return;
                }
                handleComplete(requestId, scriptId, mode, recent, npcPool, mentionedCharacter,
                        fullText, callback);
            }

            @Override
            public void onFailed(String requestId, @Nullable AppErrorCode errorCode, @Nullable String message) {
                if (!clearIfCurrent(scriptId, session)) {
                    return;
                }
                if (errorCode == AppErrorCode.CANCELLED_BY_USER) {
                    chatRepository.markRequestCancelled(requestId);
                } else {
                    chatRepository.markRequestFailed(requestId,
                            errorCode == null ? AppErrorCode.UNKNOWN.getCode() : errorCode.getCode());
                }
                callback.onGenerationFailed(requestId, errorCode);
            }
        };

        CancellableRequest startedRequest = aiRepository.streamChat(request, internal);
        synchronized (requestLock) {
            if (activeRequests.get(scriptId) == session) {
                session.request = startedRequest;
            } else {
                startedRequest.cancel();
            }
        }
        return requestId;
    }

    /** 停止当前生成（§8.7）。 */
    public void stop(String scriptId) {
        stop(scriptId, null);
    }

    /** 仅当 requestId 仍是该剧本当前请求时取消，供超时和迟到清理使用。 */
    public void stop(String scriptId, @Nullable String expectedRequestId) {
        ActiveRequest session;
        synchronized (requestLock) {
            session = activeRequests.get(scriptId);
            if (session == null || (expectedRequestId != null
                    && !expectedRequestId.equals(session.requestId))) {
                return;
            }
            activeRequests.remove(scriptId);
        }
        if (session != null && session.request != null) {
            session.request.cancel();
        }
    }

    public boolean isActive(String scriptId) {
        synchronized (requestLock) {
            return activeRequests.containsKey(scriptId);
        }
    }

    private boolean isCurrent(String scriptId, ActiveRequest session) {
        synchronized (requestLock) {
            return activeRequests.get(scriptId) == session;
        }
    }

    private boolean clearIfCurrent(String scriptId, ActiveRequest session) {
        synchronized (requestLock) {
            if (activeRequests.get(scriptId) != session) {
                return false;
            }
            activeRequests.remove(scriptId);
            return true;
        }
    }

    private void handleComplete(String requestId, String scriptId, AiRequest.Mode mode,
                                List<ChatMessage> recent, List<CharacterProfile> npcPool,
                                @Nullable CharacterProfile mentionedCharacter, String fullText,
                                Callback callback) {
        try {
            AiBatch batch = StructuredOutputParser.parse(fullText, requestId, scriptId);
            batch = AiOutputValidator.normalizeCharacterReferences(batch, npcPool);
            if (mentionedCharacter != null) {
                List<AiEvent> targeted = new ArrayList<>();
                boolean targetTurnAdded = false;
                for (AiEvent event : batch.getEvents()) {
                    if (event.getType() != AiEvent.Type.CHARACTER_TURN) {
                        targeted.add(event);
                    } else if (!targetTurnAdded
                            && mentionedCharacter.getId().equals(event.getCharacterId())) {
                        targeted.add(event);
                        targetTurnAdded = true;
                    }
                }
                batch = new AiBatch(batch.getRequestId(), batch.getScriptId(), targeted, false);
            }
            Set<String> enabledIds = AiOutputValidator.idsOf(npcPool);
            AiBatch validated = AiOutputValidator.validate(batch, enabledIds);
            boolean hadValidEvents = !validated.isEmpty();
            // 无论普通回复还是自动续演，都不能把上一轮相同内容再次写入历史。
            validated = AiResponseDeduplicator.removeNearDuplicates(validated, recent);
            if (validated.isEmpty()) {
                // 重复输出不能降级为原文，否则会把复读再次写入历史。
                if (hadValidEvents) {
                    callback.onBatchCommitted(requestId, validated);
                } else if (mode != AiRequest.Mode.AUTO_ADVANCE) {
                    insertFallbackText(requestId, scriptId, npcPool, mentionedCharacter, fullText, callback);
                } else {
                    callback.onBatchCommitted(requestId, validated);
                }
                return;
            }
            chatRepository.insertAiBatch(scriptId, validated, System.currentTimeMillis());
            callback.onBatchCommitted(requestId, validated);
        } catch (StructuredOutputParser.OutputInvalidException e) {
            insertFallbackText(requestId, scriptId, npcPool, mentionedCharacter, fullText, callback);
        }
    }

    private void insertFallbackText(String requestId, String scriptId,
                                    List<CharacterProfile> npcPool,
                                    @Nullable CharacterProfile mentionedCharacter,
                                    String fullText,
                                    Callback callback) {
        String text = StructuredOutputParser.fallbackText(fullText);
        if (text.isEmpty()) {
            chatRepository.markRequestFailed(requestId, AppErrorCode.OUTPUT_INVALID.getCode());
            callback.onGenerationFailed(requestId, AppErrorCode.OUTPUT_INVALID);
            return;
        }
        String characterId = mentionedCharacter != null ? mentionedCharacter.getId()
                : (npcPool.isEmpty() ? null : npcPool.get(0).getId());
        AiEvent event = new AiEvent(
                idGenerator.newRequestId(), AiEvent.Type.CHARACTER_TURN,
                characterId, text, 0);
        AiBatch fallback = new AiBatch(requestId, scriptId,
                java.util.Collections.singletonList(event), false);
        chatRepository.insertAiBatch(scriptId, fallback, System.currentTimeMillis());
        callback.onBatchCommitted(requestId, fallback);
    }

    @Nullable
    private CharacterProfile findMentionedCharacter(List<ChatMessage> recent,
                                                     List<CharacterProfile> npcPool) {
        if (recent == null || recent.isEmpty()) {
            return null;
        }
        ChatMessage latest = recent.get(recent.size() - 1);
        if (latest.getSide() != ChatMessage.Side.MINE
                || latest.getType() != ChatMessage.Type.CHARACTER_TEXT) {
            return null;
        }
        String content = latest.getContent();
        CharacterProfile match = null;
        int matchLength = -1;
        for (CharacterProfile character : npcPool) {
            List<String> references = new ArrayList<>();
            references.add(character.getName());
            references.addAll(character.getAliases());
            for (String reference : references) {
                if (reference == null || reference.trim().isEmpty()) {
                    continue;
                }
                String token = "@" + reference.trim();
                int index = content.indexOf(token);
                while (index >= 0) {
                    int end = index + token.length();
                    if ((end == content.length() || !Character.isLetterOrDigit(content.charAt(end)))
                            && token.length() > matchLength) {
                        match = character;
                        matchLength = token.length();
                    }
                    index = content.indexOf(token, index + 1);
                }
            }
        }
        return match;
    }
}
