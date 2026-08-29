package com.example.roleplaychat.data.mapper;

import com.example.roleplaychat.data.local.entity.AppearanceEntity;
import com.example.roleplaychat.data.local.entity.CharacterEntity;
import com.example.roleplaychat.data.local.entity.MessageEntity;
import com.example.roleplaychat.data.local.entity.ScriptEntity;
import com.example.roleplaychat.data.local.entity.SessionMemberEntity;
import com.example.roleplaychat.data.local.entity.WorldSettingEntity;
import com.example.roleplaychat.domain.model.Appearance;
import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.model.ChatMessage;
import com.example.roleplaychat.domain.model.PlayerIdentity;
import com.example.roleplaychat.domain.model.Script;
import com.example.roleplaychat.domain.model.WorldSetting;
import com.example.roleplaychat.util.JsonUtils;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 实体与领域模型双向映射（架构文档 §3.2：Data 负责映射）。
 * 枚举一律存稳定英文代码，不存 ordinal（架构文档 §5.3）。
 */
public final class EntityMapper {

    private static final Type STRING_LIST = new TypeToken<List<String>>() {
    }.getType();
    private static final Type STRING_MAP = new TypeToken<Map<String, String>>() {
    }.getType();

    private EntityMapper() {
    }

    // ---------- Script ----------

    public static Script toScript(ScriptEntity e) {
        return new Script(e.id, e.name, e.one_line, e.cover_ref, e.created_at, e.updated_at, e.sort_index);
    }

    public static ScriptEntity toEntity(Script s) {
        return new ScriptEntity(s.getId(), s.getName(), s.getOneLine(), s.getCoverRef(),
                s.getCreatedAt(), s.getUpdatedAt(), s.getSortIndex());
    }

    // ---------- WorldSetting ----------

    public static WorldSetting toWorld(WorldSettingEntity e) {
        return new WorldSetting(e.id, e.script_id, e.era, e.location,
                fromJsonList(e.factions_json), fromJsonList(e.rules_json),
                e.story_hook, e.background_full, fromJsonList(e.tags_json),
                e.version_note, e.updated_at);
    }

    public static WorldSettingEntity toEntity(WorldSetting w) {
        return new WorldSettingEntity(w.getId(), w.getScriptId(), w.getEra(), w.getLocation(),
                JsonUtils.toJson(w.getFactions()), JsonUtils.toJson(w.getRules()),
                w.getStoryHook(), w.getBackgroundFull(), JsonUtils.toJson(w.getTags()),
                w.getVersionNote(), w.getUpdatedAt());
    }

    // ---------- Character ----------

    public static CharacterProfile toProfile(CharacterEntity e) {
        return new CharacterProfile(e.id, e.script_id, e.name,
                fromJsonList(e.aliases_json), e.avatar_ref, e.gender, e.age_text,
                e.personality, e.backstory, e.speaking_style,
                fromJsonList(e.catchphrases_json), fromJsonList(e.strengths_json),
                fromJsonList(e.flaws_json), fromJsonMap(e.relationships_json),
                fromJsonList(e.sample_lines_json), e.system_prompt, e.hidden_setting,
                e.enabled, e.sort_index, e.created_at, e.updated_at, e.extra_json);
    }

    public static CharacterEntity toEntity(CharacterProfile p) {
        return new CharacterEntity(p.getId(), p.getScriptId(), p.getName(),
                JsonUtils.toJson(p.getAliases()), p.getAvatarRef(), p.getGender(), p.getAgeText(),
                p.getPersonality(), p.getBackstory(), p.getSpeakingStyle(),
                JsonUtils.toJson(p.getCatchphrases()), JsonUtils.toJson(p.getStrengths()),
                JsonUtils.toJson(p.getFlaws()), JsonUtils.toJson(p.getRelationships()),
                JsonUtils.toJson(p.getSampleLines()), p.getSystemPrompt(), p.getHiddenSetting(),
                p.isEnabled(), p.getSortIndex(), p.getCreatedAt(), p.getUpdatedAt(), p.getExtraJson());
    }

    // ---------- Message ----------

    public static ChatMessage toMessage(MessageEntity e) {
        ChatMessage.Builder b = ChatMessage.builder()
                .id(e.id)
                .scriptId(e.script_id)
                .characterId(e.character_id)
                .senderDisplayName(e.sender_name_snapshot)
                .senderAvatarRef(e.sender_avatar_snapshot)
                .appearanceSnapshotJson(e.appearance_snapshot_json)
                .playerRoleType(parseRoleType(e.player_role_type))
                .type(parseType(e.type))
                .side(parseSide(e.side))
                .content(e.content)
                .sequence(e.sequence)
                .createdAt(e.created_at)
                .status(parseStatus(e.status))
                .requestId(e.request_id)
                .batchId(e.batch_id)
                .turnIndex(e.turn_index)
                .errorCode(e.error_code)
                .metaJson(e.meta_json);
        return b.build();
    }

    public static MessageEntity toEntity(ChatMessage m) {
        return new MessageEntity(m.getId(), m.getScriptId(), m.getCharacterId(),
                m.getSenderDisplayName(), m.getSenderAvatarRef(), m.getAppearanceSnapshotJson(),
                m.getPlayerRoleType() == null ? null : m.getPlayerRoleType().name(),
                m.getType().name(), m.getSide().name(), m.getContent(),
                m.getSequence(), m.getCreatedAt(), m.getStatus().name(),
                m.getRequestId(), m.getBatchId(), m.getTurnIndex(),
                m.getErrorCode(), m.getMetaJson());
    }

    // ---------- SessionMember / Identity ----------

    public static PlayerIdentity toIdentity(SessionMemberEntity e) {
        if (e == null) {
            return null;
        }
        PlayerIdentity.RoleType roleType = parseRoleType(e.player_role_type);
        if (roleType == null) {
            roleType = PlayerIdentity.RoleType.OBSERVER;
        }
        return new PlayerIdentity(e.script_id, roleType, e.character_id, e.joined_at);
    }

    // ---------- Appearance ----------

    public static Appearance toAppearance(AppearanceEntity e) {
        return new Appearance(e.id,
                parseEnum(Appearance.ScopeType.class, e.scope_type, Appearance.ScopeType.GLOBAL),
                e.scope_id,
                parseEnum(Appearance.BackgroundType.class, e.background_type, Appearance.BackgroundType.BUILTIN),
                e.background_ref,
                parseEnum(Appearance.BackgroundMode.class, e.background_mode, Appearance.BackgroundMode.CENTER_CROP),
                e.background_dim_alpha, e.bubble_style_id, e.bubble_color, e.text_color,
                e.nickname_color, e.font_scale);
    }

    public static AppearanceEntity toEntity(Appearance a) {
        return new AppearanceEntity(a.getId(), a.getScopeType().name(), a.getScopeId(),
                a.getBackgroundType().name(), a.getBackgroundRef(), a.getBackgroundMode().name(),
                a.getBackgroundDimAlpha(), a.getBubbleStyleId(), a.getBubbleColor(),
                a.getTextColor(), a.getNicknameColor(), a.getFontScale());
    }

    // ---------- helpers ----------

    public static ChatMessage.Type parseType(String code) {
        if (code == null) {
            return ChatMessage.Type.CHARACTER_TEXT;
        }
        try {
            return ChatMessage.Type.valueOf(code);
        } catch (IllegalArgumentException e) {
            return ChatMessage.Type.CHARACTER_TEXT;
        }
    }

    public static ChatMessage.Side parseSide(String code) {
        if (code == null) {
            return ChatMessage.Side.CENTER;
        }
        try {
            return ChatMessage.Side.valueOf(code);
        } catch (IllegalArgumentException e) {
            return ChatMessage.Side.CENTER;
        }
    }

    public static ChatMessage.Status parseStatus(String code) {
        if (code == null) {
            return ChatMessage.Status.DONE;
        }
        try {
            return ChatMessage.Status.valueOf(code);
        } catch (IllegalArgumentException e) {
            return ChatMessage.Status.DONE;
        }
    }

    public static PlayerIdentity.RoleType parseRoleType(String code) {
        if (code == null) {
            return null;
        }
        try {
            return PlayerIdentity.RoleType.valueOf(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> clazz, String code, T fallback) {
        if (code == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(clazz, code);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public static List<String> fromJsonList(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = JsonUtils.fromJson(json, STRING_LIST);
        return result == null ? new ArrayList<>() : result;
    }

    public static Map<String, String> fromJsonMap(String json) {
        if (json == null || json.isEmpty()) {
            return new java.util.LinkedHashMap<>();
        }
        Map<String, String> result = JsonUtils.fromJson(json, STRING_MAP);
        return result == null ? new java.util.LinkedHashMap<>() : result;
    }
}
