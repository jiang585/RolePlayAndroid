package com.example.roleplaychat.domain.ai;

import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.model.PlayerIdentity;
import com.example.roleplaychat.domain.model.WorldSetting;

import java.util.List;
import androidx.annotation.Nullable;

/**
 * AI 上下文（架构文档 §8.1）：PromptAssembler 的输入。
 */
public final class AiContext {

    private final String scriptId;
    private final WorldSetting world;
    private final List<CharacterProfile> enabledNpcs;
    private final PlayerIdentity playerIdentity;
    private final CharacterProfile playerCharacter;
    private final String recentConversationText;
    private final String language;
    private final int maxEvents;
    @Nullable
    private final CharacterProfile mentionedCharacter;
    private final boolean automaticAdvance;

    public AiContext(String scriptId, WorldSetting world, List<CharacterProfile> enabledNpcs,
                     PlayerIdentity playerIdentity, CharacterProfile playerCharacter,
                      String recentConversationText, String language, int maxEvents) {
        this(scriptId, world, enabledNpcs, playerIdentity, playerCharacter,
                recentConversationText, language, maxEvents, null);
    }

    public AiContext(String scriptId, WorldSetting world, List<CharacterProfile> enabledNpcs,
                     PlayerIdentity playerIdentity, CharacterProfile playerCharacter,
                     String recentConversationText, String language, int maxEvents,
                     @Nullable CharacterProfile mentionedCharacter) {
        this(scriptId, world, enabledNpcs, playerIdentity, playerCharacter,
                recentConversationText, language, maxEvents, mentionedCharacter, false);
    }

    public AiContext(String scriptId, WorldSetting world, List<CharacterProfile> enabledNpcs,
                     PlayerIdentity playerIdentity, CharacterProfile playerCharacter,
                     String recentConversationText, String language, int maxEvents,
                     @Nullable CharacterProfile mentionedCharacter, boolean automaticAdvance) {
        this.scriptId = scriptId;
        this.world = world;
        this.enabledNpcs = enabledNpcs;
        this.playerIdentity = playerIdentity;
        this.playerCharacter = playerCharacter;
        this.recentConversationText = recentConversationText;
        this.language = language;
        this.maxEvents = maxEvents;
        this.mentionedCharacter = mentionedCharacter;
        this.automaticAdvance = automaticAdvance;
    }

    public String getScriptId() {
        return scriptId;
    }

    public WorldSetting getWorld() {
        return world;
    }

    public List<CharacterProfile> getEnabledNpcs() {
        return enabledNpcs;
    }

    public PlayerIdentity getPlayerIdentity() {
        return playerIdentity;
    }

    public CharacterProfile getPlayerCharacter() {
        return playerCharacter;
    }

    public String getRecentConversationText() {
        return recentConversationText;
    }

    public String getLanguage() {
        return language;
    }

    public int getMaxEvents() {
        return maxEvents;
    }

    @Nullable
    public CharacterProfile getMentionedCharacter() {
        return mentionedCharacter;
    }

    public boolean isAutomaticAdvance() {
        return automaticAdvance;
    }
}
