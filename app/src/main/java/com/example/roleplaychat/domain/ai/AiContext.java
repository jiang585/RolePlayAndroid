package com.example.roleplaychat.domain.ai;

import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.model.PlayerIdentity;
import com.example.roleplaychat.domain.model.WorldSetting;

import java.util.List;
import androidx.annotation.Nullable;

/**
 * AI 上下文（架构文档 §8.1）：PromptAssembler 的输入。
 * 含回复编排约束：每轮最多回复角色数（maxResponders）、剧本级扮演要求
 * （styleDirective）与最近发言者名单（recentSpeakerNames，用于抑制连续发言）。
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
    private final int maxResponders;
    @Nullable
    private final String styleDirective;
    private final List<String> recentSpeakerNames;

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
        this(scriptId, world, enabledNpcs, playerIdentity, playerCharacter,
                recentConversationText, language, maxEvents, mentionedCharacter, automaticAdvance,
                WorldSetting.DEFAULT_MAX_RESPONDERS, null, null);
    }

    public AiContext(String scriptId, WorldSetting world, List<CharacterProfile> enabledNpcs,
                     PlayerIdentity playerIdentity, CharacterProfile playerCharacter,
                     String recentConversationText, String language, int maxEvents,
                     @Nullable CharacterProfile mentionedCharacter, boolean automaticAdvance,
                     int maxResponders, @Nullable String styleDirective,
                     @Nullable List<String> recentSpeakerNames) {
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
        this.maxResponders = Math.max(1, maxResponders);
        this.styleDirective = styleDirective;
        this.recentSpeakerNames = recentSpeakerNames == null
                ? java.util.Collections.emptyList() : recentSpeakerNames;
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

    /** 本轮最多回复角色数（@ 提及时不受此限）。 */
    public int getMaxResponders() {
        return maxResponders;
    }

    /** 剧本级扮演要求；null 表示未设置。 */
    @Nullable
    public String getStyleDirective() {
        return styleDirective;
    }

    /** 最近刚发言的角色名（去重、按发言先后）。 */
    public List<String> getRecentSpeakerNames() {
        return recentSpeakerNames;
    }
}
