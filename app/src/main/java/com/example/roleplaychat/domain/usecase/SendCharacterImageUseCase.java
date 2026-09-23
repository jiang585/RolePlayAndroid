package com.example.roleplaychat.domain.usecase;

import com.example.roleplaychat.domain.ai.ImageGenerationScheduler;
import com.example.roleplaychat.domain.model.AiImageAction;
import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.model.ChatMessage;
import com.example.roleplaychat.domain.model.Script;
import com.example.roleplaychat.domain.repository.CharacterRepository;
import com.example.roleplaychat.domain.repository.ChatRepository;
import com.example.roleplaychat.domain.repository.ScriptRepository;

import java.util.Collections;
import java.util.UUID;

/** 用户绕过 LLM 直接要求指定角色发送图片。 */
public final class SendCharacterImageUseCase {
    private final ScriptRepository scriptRepository;
    private final CharacterRepository characterRepository;
    private final ChatRepository chatRepository;
    private final ImageGenerationScheduler scheduler;

    public SendCharacterImageUseCase(ScriptRepository scriptRepository, CharacterRepository characterRepository,
                                     ChatRepository chatRepository, ImageGenerationScheduler scheduler) {
        this.scriptRepository = scriptRepository; this.characterRepository = characterRepository;
        this.chatRepository = chatRepository; this.scheduler = scheduler;
    }

    public boolean send(String scriptId, String characterId, String description, long now) {
        Script script = scriptRepository.getById(scriptId);
        CharacterProfile character = characterRepository.getById(characterId);
        if (script == null || !script.isVisual() || character == null || !character.isEnabled()) return false;
        ChatMessage placeholder = chatRepository.insertImagePlaceholder(scriptId, characterId, now);
        AiImageAction action = new AiImageAction(UUID.randomUUID().toString(), placeholder.getId(), characterId,
                AiImageAction.Intent.PHOTO_SHARE, AiImageAction.Trigger.MANUAL_USER_REQUEST,
                description == null || description.trim().isEmpty() ? "角色给用户发送一张照片" : description.trim(),
                "半身或全身，正面清晰", null, null);
        scheduler.enqueue(scriptId, Collections.singletonList(action), "manual-" + placeholder.getId(), now);
        return true;
    }
}
