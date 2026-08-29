package com.example.roleplaychat.domain.ai;

import com.example.roleplaychat.domain.model.AiBatch;
import com.example.roleplaychat.domain.model.AiEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;

/**
 * AI 输出校验器（架构文档 §8.4）：
 * events 数量 1~8；character_turn.character_id 必须属于启用 NPC ID 集合；
 * 未知角色/超长事件丢弃并产生可见的非敏感提示。
 */
public final class AiOutputValidator {

    public static final int MAX_EVENTS = 8;
    public static final int MAX_EVENT_CONTENT = 4096;

    private AiOutputValidator() {
    }

    /** 校验并返回清洗后的批次。 */
    public static AiBatch validate(AiBatch batch, Set<String> enabledCharacterIds) {
        List<AiEvent> valid = new ArrayList<>();
        for (AiEvent event : batch.getEvents()) {
            if (valid.size() >= MAX_EVENTS) {
                break;
            }
            if (event.getType() == AiEvent.Type.CHARACTER_TURN) {
                if (event.getCharacterId() == null
                        || !enabledCharacterIds.contains(event.getCharacterId())) {
                    continue; // UNKNOWN_CHARACTER：丢弃该事件
                }
            }
            if (event.getContent().length() > MAX_EVENT_CONTENT) {
                String trimmed = event.getContent().substring(0, MAX_EVENT_CONTENT);
                valid.add(new AiEvent(event.getEventId(), event.getType(),
                        event.getCharacterId(), trimmed, event.getTurnIndex()));
                continue;
            }
            valid.add(event);
        }
        return new AiBatch(batch.getRequestId(), batch.getScriptId(), valid,
                batch.shouldContinueScene());
    }

    /** 兼容模型把 character_id 填成角色姓名/别名的情况，统一转换为本地 ID。 */
    public static AiBatch normalizeCharacterReferences(AiBatch batch,
            List<com.example.roleplaychat.domain.model.CharacterProfile> characters) {
        List<AiEvent> normalized = new ArrayList<>();
        for (AiEvent event : batch.getEvents()) {
            if (event.getType() != AiEvent.Type.CHARACTER_TURN
                    || event.getCharacterId() == null) {
                normalized.add(event);
                continue;
            }
            String reference = event.getCharacterId().trim();
            boolean matched = false;
            for (com.example.roleplaychat.domain.model.CharacterProfile character : characters) {
                if (reference.equals(character.getId())
                        || reference.equalsIgnoreCase(character.getName())
                        || character.getAliases().stream().anyMatch(alias ->
                        reference.equalsIgnoreCase(alias))) {
                    normalized.add(new AiEvent(event.getEventId(), event.getType(),
                            character.getId(), event.getContent(), event.getTurnIndex()));
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                // Do not invent an in-scene speaker, but preserve the model's action/text instead
                // of silently losing it because its character reference was not resolvable.
                normalized.add(new AiEvent(event.getEventId(), AiEvent.Type.NARRATION,
                        null, event.getContent(), event.getTurnIndex()));
            }
        }
        return new AiBatch(batch.getRequestId(), batch.getScriptId(), normalized,
                batch.shouldContinueScene());
    }

    /** 从角色列表构造启用 ID 集合。 */
    public static Set<String> idsOf(List<com.example.roleplaychat.domain.model.CharacterProfile> characters) {
        Set<String> ids = new HashSet<>();
        if (characters != null) {
            for (com.example.roleplaychat.domain.model.CharacterProfile character : characters) {
                ids.add(character.getId());
            }
        }
        return ids;
    }
}
