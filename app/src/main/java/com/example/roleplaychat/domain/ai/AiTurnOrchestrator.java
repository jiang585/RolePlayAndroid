package com.example.roleplaychat.domain.ai;

import androidx.annotation.Nullable;

import com.example.roleplaychat.domain.model.AiBatch;
import com.example.roleplaychat.domain.model.AiEvent;
import com.example.roleplaychat.domain.model.AiImageAction;
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
import com.example.roleplaychat.domain.repository.MomentRepository;
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

    /** 参与发言频率抑制的最近 AI 消息条数。 */
    private static final int RECENT_SPEAKER_WINDOW = 6;

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
    private final MomentRepository momentRepository;
    private final AiRepository aiRepository;
    private final IdGenerator idGenerator;
    private final String language;
    @Nullable
    private final ImageGenerationScheduler imageGenerationScheduler;

    private final Object requestLock = new Object();
    private final Map<String, ActiveRequest> activeRequests = new HashMap<>();
    /** 每次停止都会推进版本，即使网络回调已完成但尚未落库也不能再写入。 */
    private final Map<String, Long> requestEpochs = new HashMap<>();

    private static final class ActiveRequest {
        private final String requestId;
        private final long epoch;
        @Nullable
        private CancellableRequest request;

        private ActiveRequest(String requestId, long epoch) {
            this.requestId = requestId;
            this.epoch = epoch;
        }
    }

    public AiTurnOrchestrator(ScriptRepository scriptRepository, WorldRepository worldRepository,
                               CharacterRepository characterRepository, ChatRepository chatRepository,
                               SettingsRepository settingsRepository, AiRepository aiRepository,
                               IdGenerator idGenerator, String language) {
        this(scriptRepository, worldRepository, characterRepository, chatRepository,
                settingsRepository, null, aiRepository, idGenerator, language, null);
    }

    public AiTurnOrchestrator(ScriptRepository scriptRepository, WorldRepository worldRepository,
                               CharacterRepository characterRepository, ChatRepository chatRepository,
                               SettingsRepository settingsRepository, MomentRepository momentRepository, AiRepository aiRepository,
                              IdGenerator idGenerator, String language) {
        this(scriptRepository, worldRepository, characterRepository, chatRepository, settingsRepository,
                momentRepository, aiRepository, idGenerator, language, null);
    }

    public AiTurnOrchestrator(ScriptRepository scriptRepository, WorldRepository worldRepository,
                               CharacterRepository characterRepository, ChatRepository chatRepository,
                               SettingsRepository settingsRepository, MomentRepository momentRepository, AiRepository aiRepository,
                               IdGenerator idGenerator, String language,
                               @Nullable ImageGenerationScheduler imageGenerationScheduler) {
        this.scriptRepository = scriptRepository;
        this.worldRepository = worldRepository;
        this.characterRepository = characterRepository;
        this.chatRepository = chatRepository;
        this.settingsRepository = settingsRepository;
        this.momentRepository = momentRepository;
        this.aiRepository = aiRepository;
        this.idGenerator = idGenerator;
        this.language = language;
        this.imageGenerationScheduler = imageGenerationScheduler;
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
        ActiveRequest session;

        ActiveRequest previous;
        synchronized (requestLock) {
            long epoch = requestEpochs.getOrDefault(scriptId, 0L);
            session = new ActiveRequest(requestId, epoch);
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
        // 只追加的剧情上下文：纪元内每轮只是在末尾追加新消息，前缀缓存才可能命中。
        String conversation = ContextWindowPolicy.toHistoryContext(allMessages, recentCount);

        // 剧本级对话规则：每轮回复上限与扮演要求；最近发言者用于抑制"轮流表态"。
        int maxResponders = world == null ? WorldSetting.DEFAULT_MAX_RESPONDERS
                : world.getMaxRespondersPerTurn();
        String styleDirective = world == null ? null : world.getChatStyleDirective();
        List<String> recentSpeakerNames = collectRecentSpeakerNames(allMessages, RECENT_SPEAKER_WINDOW);

        AiContext context = new AiContext(scriptId, world, npcPool, identity, playerCharacter,
                conversation, language, 8, mentionedCharacter, mode == AiRequest.Mode.AUTO_ADVANCE,
                mode == AiRequest.Mode.MOMENT_INTERACTION, maxResponders, styleDirective, recentSpeakerNames);

        // 朋友圈上下文属于「本轮才看得到」的信息，必须和其它逐轮变化的内容一起排在最后一条；
        // 一旦插到剧情上下文之前，每轮变化的朋友圈就会把整段对话一起踢出缓存。
        String socialContext = momentRepository == null ? "" : momentRepository.buildPromptContext(scriptId);
        // 用户最新输入/旁白追加在本轮指令末尾，优先级高于历史上下文和朋友圈摘要。
        String authoritative = PromptAssembler.buildAuthoritativeUserDirective(allMessages);
        if (!authoritative.isEmpty()) socialContext = socialContext + "\n" + authoritative;
        List<PromptMessage> messages = PromptAssembler.buildMessages(context, socialContext);
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
                        maxResponders, fullText, session, callback);
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
            if (expectedRequestId != null && (session == null
                    || !expectedRequestId.equals(session.requestId))) {
                return;
            }
            requestEpochs.put(scriptId, requestEpochs.getOrDefault(scriptId, 0L) + 1L);
            if (session == null) return;
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
                                 @Nullable CharacterProfile mentionedCharacter, int maxResponders,
                                 String fullText, ActiveRequest session, Callback callback) {
        try {
            AiBatch batch = StructuredOutputParser.parse(fullText, requestId, scriptId);
            batch = AiOutputValidator.normalizeCharacterReferences(batch, npcPool);
            // 明确的“发照片/自拍给我看看”是应用级动作，不能依赖模型是否记得输出 image_actions。
            // 先在本地补动作，再走原有校验和入队，保证角色仍可正常回复文字。
            batch = ensureExplicitImageAction(batch, requestId, mode, recent, npcPool, mentionedCharacter);
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
                batch = new AiBatch(batch.getRequestId(), batch.getScriptId(), targeted, false,
                        batch.shouldAwaitPlayer(), batch.getMomentActions(), batch.getImageActions());
            } else {
                // 不 @ 时执行人数上限硬约束（@ 提及的路径优先级更高，已保证单人）。
                batch = AiOutputValidator.capResponders(batch, maxResponders);
            }
            Set<String> enabledIds = AiOutputValidator.idsOf(npcPool);
            AiBatch validated = AiOutputValidator.validate(batch, enabledIds);
            boolean hadValidOutput = !validated.isEmpty() || !validated.getMomentActions().isEmpty();
            // 无论普通回复还是自动续演，都不能把上一轮相同内容再次写入历史。
            validated = AiResponseDeduplicator.removeNearDuplicates(validated, recent);
            if (validated.isEmpty()) {
                // 重复输出不能降级为原文，否则会把复读再次写入历史。
                if (hadValidOutput) {
                    callback.onBatchCommitted(requestId, validated);
                } else if (mode != AiRequest.Mode.AUTO_ADVANCE) {
                    insertFallbackText(requestId, scriptId, npcPool, mentionedCharacter, recent, fullText, session, callback);
                } else {
                    callback.onBatchCommitted(requestId, validated);
                }
                return;
            }
            if (!commitIfNotDiscarded(scriptId, session, validated, enabledIds, requestId)) {
                callback.onGenerationFailed(requestId, AppErrorCode.CANCELLED_BY_USER);
                return;
            }
            callback.onBatchCommitted(requestId, validated);
        } catch (StructuredOutputParser.OutputInvalidException e) {
            insertFallbackText(requestId, scriptId, npcPool, mentionedCharacter, recent, fullText, session, callback);
        }
    }

    /** 与 stop() 使用同一把锁，把“停止”和“批次落库”线性化，避免清空后迟到写入。 */
    private boolean commitIfNotDiscarded(String scriptId, ActiveRequest session, AiBatch batch,
                                         Set<String> enabledIds, String requestId) {
        synchronized (requestLock) {
            if (requestEpochs.getOrDefault(scriptId, 0L) != session.epoch) return false;
            long now = System.currentTimeMillis();
            chatRepository.insertAiBatch(scriptId, batch, now);
            if (momentRepository != null && !batch.getMomentActions().isEmpty()) {
                momentRepository.applyAiActions(scriptId, batch.getMomentActions(), enabledIds,
                        chatRepository.maxSequence(scriptId), requestId, now);
            }
            if (imageGenerationScheduler != null && !batch.getImageActions().isEmpty()) {
                imageGenerationScheduler.enqueue(scriptId, batch.getImageActions(), requestId, now);
            }
            return true;
        }
    }

    private void insertFallbackText(String requestId, String scriptId,
                                    List<CharacterProfile> npcPool,
                                    @Nullable CharacterProfile mentionedCharacter,
                                    List<ChatMessage> recent,
                                    String fullText,
                                    ActiveRequest session,
                                    Callback callback) {
        String text = StructuredOutputParser.fallbackText(fullText);
        if (text.isEmpty()) {
            chatRepository.markRequestFailed(requestId, AppErrorCode.OUTPUT_INVALID.getCode());
            callback.onGenerationFailed(requestId, AppErrorCode.OUTPUT_INVALID);
            return;
        }
        // 结构化输出失败时不能把未知台词强行归给列表第一个角色。
        String characterId = mentionedCharacter != null ? mentionedCharacter.getId()
                : (npcPool.size() == 1 ? npcPool.get(0).getId() : null);
        AiEvent.Type fallbackType = characterId == null ? AiEvent.Type.NARRATION : AiEvent.Type.CHARACTER_TURN;
        AiEvent event = new AiEvent(
                idGenerator.newRequestId(), fallbackType,
                characterId, text, 0);
        List<AiImageAction> imageActions = new ArrayList<>();
        AiImageAction forced = buildExplicitImageAction(requestId, AiRequest.Mode.NORMAL_REPLY,
                recent, npcPool, mentionedCharacter);
        if (forced != null) imageActions.add(forced);
        AiBatch fallback = new AiBatch(requestId, scriptId,
                java.util.Collections.singletonList(event), false, false,
                java.util.Collections.emptyList(), imageActions);
        if (!commitIfNotDiscarded(scriptId, session, fallback,
                AiOutputValidator.idsOf(npcPool), requestId)) {
            callback.onGenerationFailed(requestId, AppErrorCode.CANCELLED_BY_USER);
            return;
        }
        callback.onBatchCommitted(requestId, fallback);
    }

    /** 最近 N 条 AI 角色发言的去重发言者名单（按发言先后，供提示词抑制频繁发言）。 */
    private static List<String> collectRecentSpeakerNames(List<ChatMessage> allMessages, int window) {
        List<String> names = new ArrayList<>();
        if (allMessages == null) {
            return names;
        }
        // 只保留窗口内的 AI 角色发言，按时间正序去重。
        List<ChatMessage> windowMessages = new ArrayList<>();
        for (ChatMessage message : allMessages) {
            if (isAiCharacterTurn(message)) {
                windowMessages.add(message);
                if (windowMessages.size() > window) {
                    windowMessages.remove(0);
                }
            }
        }
        for (ChatMessage message : windowMessages) {
            String name = message.getSenderDisplayName();
            if (name != null && !name.isEmpty() && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private static boolean isAiCharacterTurn(ChatMessage message) {
        return message.getSide() == ChatMessage.Side.THEIRS
                && message.getType() == ChatMessage.Type.CHARACTER_TEXT
                && message.getCharacterId() != null;
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

    private AiBatch ensureExplicitImageAction(AiBatch batch, String requestId, AiRequest.Mode mode,
                                              List<ChatMessage> recent, List<CharacterProfile> npcPool,
                                              @Nullable CharacterProfile mentionedCharacter) {
        AiImageAction forced = buildExplicitImageAction(requestId, mode, recent, npcPool, mentionedCharacter);
        if (forced == null) return batch;
        for (AiImageAction existing : batch.getImageActions()) {
            if (forced.getCharacterId().equals(existing.getCharacterId())
                    && existing.isIncludeCharacter()) return batch;
        }
        List<AiImageAction> actions = new ArrayList<>(batch.getImageActions());
        actions.add(forced);
        return new AiBatch(batch.getRequestId(), batch.getScriptId(), batch.getEvents(),
                batch.shouldContinueScene(), batch.shouldAwaitPlayer(), batch.getMomentActions(), actions);
    }

    @Nullable
    private AiImageAction buildExplicitImageAction(String requestId, AiRequest.Mode mode,
                                                   List<ChatMessage> recent,
                                                   List<CharacterProfile> npcPool,
                                                   @Nullable CharacterProfile mentionedCharacter) {
        if (mode == AiRequest.Mode.AUTO_ADVANCE || !containsPhotoRequest(recent)) return null;
        String userText = latestPlayerText(recent);
        if (userText == null || userText.trim().isEmpty()) return null;
        CharacterProfile target = mentionedCharacter;
        if (target == null) target = findNamedCharacter(userText, npcPool);
        if (target == null && npcPool.size() == 1) target = npcPool.get(0);
        if (target == null) target = findLatestCharacterTurn(recent, npcPool);
        if (target == null) return null;
        AiImageAction.Intent intent = userText.contains("自拍")
                ? AiImageAction.Intent.SELFIE
                : userText.contains("穿搭") || userText.contains("衣服") || userText.contains("穿着")
                ? AiImageAction.Intent.OUTFIT_SHOW : AiImageAction.Intent.PHOTO_SHARE;
        return new AiImageAction(requestId + ":explicit-photo", null, target.getId(), intent,
                AiImageAction.Trigger.EXPLICIT_USER_REQUEST,
                "玩家明确要求" + target.getName() + "发送照片：" + userText,
                userText.contains("全身") ? "全身，9:16 竖屏" : "自然的半身或全身，9:16 竖屏",
                null, null, true);
    }

    private static boolean containsPhotoRequest(List<ChatMessage> recent) {
        String text = latestPlayerText(recent);
        if (text == null) return false;
        boolean photo = text.contains("照片") || text.contains("图片") || text.contains("自拍")
                || text.contains("相片") || text.contains("靓照") || text.contains("拍给我")
                || text.contains("拍一张") || text.contains("发张");
        boolean request = text.contains("发") || text.contains("拍") || text.contains("给我")
                || text.contains("传") || text.contains("分享") || text.contains("来一张")
                || text.contains("看看");
        return photo && request;
    }

    @Nullable
    private static String latestPlayerText(List<ChatMessage> recent) {
        if (recent == null) return null;
        for (int i = recent.size() - 1; i >= 0; i--) {
            ChatMessage message = recent.get(i);
            if (message != null && message.getSide() == ChatMessage.Side.MINE
                    && message.getType() == ChatMessage.Type.CHARACTER_TEXT) {
                return message.getContent();
            }
        }
        return null;
    }

    @Nullable
    private static CharacterProfile findNamedCharacter(String text, List<CharacterProfile> characters) {
        CharacterProfile match = null;
        int longest = 0;
        for (CharacterProfile character : characters) {
            List<String> names = new ArrayList<>();
            names.add(character.getName());
            names.addAll(character.getAliases());
            for (String name : names) {
                if (name != null && name.length() > longest && text.contains(name)) {
                    match = character;
                    longest = name.length();
                }
            }
        }
        return match;
    }

    @Nullable
    private static CharacterProfile findLatestCharacterTurn(List<ChatMessage> recent,
                                                            List<CharacterProfile> characters) {
        if (recent == null) return null;
        for (int i = recent.size() - 1; i >= 0; i--) {
            ChatMessage message = recent.get(i);
            if (message == null || message.getSide() != ChatMessage.Side.THEIRS
                    || message.getCharacterId() == null) continue;
            for (CharacterProfile character : characters) {
                if (character.getId().equals(message.getCharacterId())) return character;
            }
        }
        return null;
    }
}
