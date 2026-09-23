package com.example.roleplaychat.domain.model;

import androidx.annotation.Nullable;

import java.util.Objects;

/** Structured request for an asynchronously generated character image. */
public final class AiImageAction {
    public enum Intent { SELFIE, SCENE_SHARE, OUTFIT_SHOW, PHOTO_SHARE }
    public enum Trigger { EXPLICIT_USER_REQUEST, SPONTANEOUS_CHARACTER_SHARE, SYSTEM_DETECTED_INTENT, MANUAL_USER_REQUEST }

    private final String actionId;
    @Nullable private final String messageId;
    private final String characterId;
    private final Intent intent;
    private final Trigger trigger;
    private final String scene;
    @Nullable private final String framing;
    @Nullable private final String mood;
    @Nullable private final String outfit;
    private final boolean includeCharacter;

    public AiImageAction(String actionId, String characterId, Intent intent, Trigger trigger,
                         String scene, @Nullable String framing, @Nullable String mood,
                         @Nullable String outfit) {
        this(actionId, null, characterId, intent, trigger, scene, framing, mood, outfit);
    }

    public AiImageAction(String actionId, @Nullable String messageId, String characterId, Intent intent, Trigger trigger,
                         String scene, @Nullable String framing, @Nullable String mood,
                         @Nullable String outfit) {
        this(actionId, messageId, characterId, intent, trigger, scene, framing, mood, outfit, true);
    }

    public AiImageAction(String actionId, @Nullable String messageId, String characterId, Intent intent, Trigger trigger,
                         String scene, @Nullable String framing, @Nullable String mood,
                         @Nullable String outfit, boolean includeCharacter) {
        this.actionId = Objects.requireNonNull(actionId);
        this.messageId = messageId;
        this.characterId = Objects.requireNonNull(characterId);
        this.intent = Objects.requireNonNull(intent);
        this.trigger = Objects.requireNonNull(trigger);
        this.scene = Objects.requireNonNull(scene);
        this.framing = framing;
        this.mood = mood;
        this.outfit = outfit;
        this.includeCharacter = includeCharacter;
    }

    public String getActionId() { return actionId; }
    @Nullable public String getMessageId() { return messageId; }
    public String getCharacterId() { return characterId; }
    public Intent getIntent() { return intent; }
    public Trigger getTrigger() { return trigger; }
    public String getScene() { return scene; }
    @Nullable public String getFraming() { return framing; }
    @Nullable public String getMood() { return mood; }
    @Nullable public String getOutfit() { return outfit; }
    public boolean isIncludeCharacter() { return includeCharacter; }
}
