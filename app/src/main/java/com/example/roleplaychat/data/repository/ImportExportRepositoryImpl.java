package com.example.roleplaychat.data.repository;

import androidx.annotation.Nullable;

import com.example.roleplaychat.data.file.ExportWriter;
import com.example.roleplaychat.data.file.ImportReader;
import com.example.roleplaychat.data.file.LocalAssetStore;
import com.example.roleplaychat.data.file.ScriptPackageArchive;
import com.example.roleplaychat.data.local.AppDatabase;
import com.example.roleplaychat.data.local.entity.CharacterEntity;
import com.example.roleplaychat.data.local.entity.ImportLogEntity;
import com.example.roleplaychat.data.local.entity.MessageEntity;
import com.example.roleplaychat.data.local.entity.SessionMemberEntity;
import com.example.roleplaychat.data.mapper.CharacterCardMapper;
import com.example.roleplaychat.data.mapper.EntityMapper;
import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.AppErrorCode;
import com.example.roleplaychat.domain.model.ChatMessage;
import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.model.WorldSetting;
import com.example.roleplaychat.domain.repository.ImportExportRepository;
import com.example.roleplaychat.util.JsonUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 导入导出仓库实现（架构文档 §9）。
 */
public class ImportExportRepositoryImpl implements ImportExportRepository {

    private final AppDatabase db;
    private final LocalAssetStore assetStore;

    public ImportExportRepositoryImpl(AppDatabase db, LocalAssetStore assetStore) {
        this.db = db;
        this.assetStore = assetStore;
    }

    @Override
    @Nullable
    public AppError exportCharacterCard(String characterId, File target, boolean includeHidden) {
        CharacterEntity entity = db.characterDao().getById(characterId);
        if (entity == null) {
            return AppError.of(AppErrorCode.IMPORT_INVALID, "character not found", false);
        }
        CharacterProfile profile = EntityMapper.toProfile(entity);
        CharacterCardMapper.Envelope envelope = CharacterCardMapper.toEnvelope(profile, includeHidden);
        if (profile.getAvatarRef() != null) {
            File avatarFile = assetStore.resolve(profile.getAvatarRef());
            if (avatarFile != null) {
                CharacterCardMapper.Avatar avatar = new CharacterCardMapper.Avatar();
                avatar.mode = "embedded";
                avatar.mediaType = "image/webp";
                try {
                    avatar.dataBase64 = Base64.getEncoder().encodeToString(
                            java.nio.file.Files.readAllBytes(avatarFile.toPath()));
                } catch (java.io.IOException e) {
                    avatar = null;
                }
                envelope.data.avatar = avatar;
            }
        }
        return ExportWriter.writeCharacterCard(target, envelope);
    }

    @Override
    @Nullable
    public AppError exportWorld(String scriptId, File target) {
        WorldSetting world = loadWorld(scriptId);
        if (world == null) {
            return AppError.of(AppErrorCode.IMPORT_INVALID, "world not found", false);
        }
        return ExportWriter.writeWorld(target, world);
    }

    @Override
    @Nullable
    public AppError exportChatJson(String scriptId, File target) {
        return ExportWriter.writeChatJson(target, loadAllMessages(scriptId));
    }

    @Override
    @Nullable
    public AppError exportChatTxt(String scriptId, File target) {
        return ExportWriter.writeChatTxt(target, loadAllMessages(scriptId));
    }

    @Override
    @Nullable
    public AppError exportChatPdf(String scriptId, File target) {
        return ExportWriter.writeChatPdf(target, loadAllMessages(scriptId));
    }

    @Override
    @Nullable
    public AppError exportScriptPackage(String scriptId, File targetDir) {
        com.example.roleplaychat.data.local.entity.ScriptEntity script = db.scriptDao().getById(scriptId);
        if (script == null) {
            return AppError.of(AppErrorCode.IMPORT_INVALID, "script not found", false);
        }
        try {
            java.util.Map<String, String> textEntries = new java.util.LinkedHashMap<>();
            java.util.Map<String, File> assetEntries = new java.util.LinkedHashMap<>();

            textEntries.put("script.json", JsonUtils.toJson(script));

            WorldSetting world = loadWorld(scriptId);
            if (world != null) {
                textEntries.put("world.json", JsonUtils.toJson(world));
            }

            List<CharacterEntity> characters = db.characterDao().getAllByScriptId(scriptId);
            for (CharacterEntity character : characters) {
                CharacterCardMapper.Envelope envelope =
                        CharacterCardMapper.toEnvelope(EntityMapper.toProfile(character), false);
                textEntries.put("characters/" + character.id + ".json", JsonUtils.toJson(envelope));
            }

            ExportWriter.ChatEnvelope chatEnvelope = buildChatEnvelope(loadAllMessages(scriptId));
            textEntries.put("messages/messages.json", JsonUtils.toJson(chatEnvelope));

            File zip = ScriptPackageArchive.createPackage(targetDir, script.name, textEntries, assetEntries);
            if (zip == null) {
                return AppError.of(AppErrorCode.UNKNOWN, "package failed", false);
            }
            return null;
        } catch (Exception e) {
            return AppError.of(AppErrorCode.UNKNOWN, e.getMessage(), false);
        }
    }

    @Override
    @Nullable
    public String importCharacterCard(File source, String targetScriptId, @Nullable AppError[] error) {
        CharacterCardMapper.Data data = ImportReader.parseCard(source, error);
        if (data == null) {
            logImport("character", "?", source.getName(), false, getError(error));
            return null;
        }
        if (db.scriptDao().getById(targetScriptId) == null) {
            setError(error, "target script not found");
            logImport("character", "?", source.getName(), false, getError(error));
            return null;
        }
        long now = System.currentTimeMillis();
        int sortIndex = db.characterDao().nextSortIndex(targetScriptId);
        String characterId = java.util.UUID.randomUUID().toString();
        CharacterProfile profile = CharacterCardMapper.toProfile(data, characterId, targetScriptId, true, sortIndex, now);
        String avatarRef = localizeAvatar(data.avatar);
        if (avatarRef != null) {
            profile = withAvatar(profile, avatarRef);
        }
        CharacterProfile finalProfile = profile;
        db.runInTransaction(() -> {
            db.characterDao().insert(EntityMapper.toEntity(finalProfile));
            db.sessionMemberDao().upsertMember(java.util.UUID.randomUUID().toString(),
                    targetScriptId, characterId, SessionMemberEntity.MEMBER_NPC, null, true, now);
        });
        logImport("character", String.valueOf(CharacterCardMapper.SCHEMA_VERSION), source.getName(), true, null);
        return characterId;
    }

    @Override
    @Nullable
    public String importWorld(File source, String targetScriptId, @Nullable AppError[] error) {
        String json = ImportReader.readJson(source, error);
        if (json == null) {
            logImport("world", "?", source.getName(), false, getError(error));
            return null;
        }
        if (db.scriptDao().getById(targetScriptId) == null) {
            setError(error, "target script not found");
            logImport("world", "?", source.getName(), false, getError(error));
            return null;
        }
        try {
            ExportWriter.Envelope envelope = JsonUtils.fromJson(json, ExportWriter.Envelope.class);
            if (envelope == null || envelope.data == null) {
                setError(error, "invalid world envelope");
                logImport("world", "?", source.getName(), false, getError(error));
                return null;
            }
            ExportWriter.WorldDto dto = JsonUtils.fromJson(JsonUtils.toJson(envelope.data), ExportWriter.WorldDto.class);
            if (dto == null) {
                setError(error, "invalid world data");
                logImport("world", "?", source.getName(), false, getError(error));
                return null;
            }
            WorldSetting world = new WorldSetting(
                    java.util.UUID.randomUUID().toString(),
                    targetScriptId,
                    dto.era,
                    dto.location,
                    dto.factions == null ? new ArrayList<>() : dto.factions,
                    dto.rules == null ? new ArrayList<>() : dto.rules,
                    dto.storyHook,
                    dto.backgroundFull,
                    dto.tags == null ? new ArrayList<>() : dto.tags,
                    dto.versionNote,
                    System.currentTimeMillis());
            db.worldSettingDao().updateByScriptId(targetScriptId,
                    world.getEra(), world.getLocation(),
                    JsonUtils.toJson(world.getFactions()), JsonUtils.toJson(world.getRules()),
                    world.getStoryHook(), world.getBackgroundFull(), JsonUtils.toJson(world.getTags()),
                    world.getVersionNote(), world.getUpdatedAt());
            logImport("world", "1", source.getName(), true, null);
            return targetScriptId;
        } catch (Exception e) {
            setError(error, "parse error");
            logImport("world", "?", source.getName(), false, getError(error));
            return null;
        }
    }

    @Override
    @Nullable
    public AppError importChat(File source, String targetScriptId) {
        String json = ImportReader.readJson(source, null);
        if (json == null) {
            return AppError.of(AppErrorCode.IMPORT_INVALID, "invalid chat file", false);
        }
        try {
            ExportWriter.ChatEnvelope envelope = JsonUtils.fromJson(json, ExportWriter.ChatEnvelope.class);
            if (envelope == null || envelope.messages == null) {
                return AppError.of(AppErrorCode.IMPORT_INVALID, "invalid chat envelope", false);
            }
            long now = System.currentTimeMillis();
            List<MessageEntity> entities = new ArrayList<>();
            for (ExportWriter.ChatItem item : envelope.messages) {
                if (item.content == null) {
                    continue;
                }
                ChatMessage.Type type = EntityMapper.parseType(item.type);
                ChatMessage.Side side = type == ChatMessage.Type.NARRATION || type == ChatMessage.Type.SYSTEM_EVENT
                        ? ChatMessage.Side.CENTER : ChatMessage.Side.THEIRS;
                MessageEntity entity = new MessageEntity(
                        java.util.UUID.randomUUID().toString(),
                        targetScriptId,
                        null,
                        item.senderName,
                        null,
                        null,
                        null,
                        type.name(),
                        side.name(),
                        item.content,
                        0,
                        item.createdAt > 0 ? item.createdAt : now,
                        ChatMessage.Status.DONE.name(),
                        null,
                        null,
                        null,
                        null,
                        null);
                entities.add(entity);
            }
            if (!entities.isEmpty()) {
                db.runInTransaction(() -> db.messageDao().insertAiBatchTx(entities));
            }
            logImport("chat", "1", source.getName(), true, null);
            return null;
        } catch (Exception e) {
            logImport("chat", "?", source.getName(), false,
                    AppError.of(AppErrorCode.IMPORT_INVALID, "parse error", false));
            return AppError.of(AppErrorCode.IMPORT_INVALID, "parse error", false);
        }
    }

    @Override
    public String previewCharacterCard(File source) {
        CharacterCardMapper.Data data = ImportReader.parseCard(source, null);
        if (data == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("姓名：").append(data.name == null ? "" : data.name).append('\n');
        if (data.gender != null) {
            sb.append("性别：").append(data.gender).append('\n');
        }
        if (data.personality != null) {
            sb.append("性格：").append(data.personality).append('\n');
        }
        if (data.backstory != null) {
            sb.append("背景：").append(data.backstory).append('\n');
        }
        if (data.sampleLines != null && !data.sampleLines.isEmpty()) {
            sb.append("示例台词：").append(data.sampleLines.get(0)).append('\n');
        }
        return sb.toString();
    }

    @Override
    public List<String> supportedImportExtensions() {
        List<String> list = new ArrayList<>();
        list.add("application/json");
        list.add("text/json");
        list.add("text/plain");
        return list;
    }

    // ---------- helpers ----------

    @Nullable
    private WorldSetting loadWorld(String scriptId) {
        com.example.roleplaychat.data.local.entity.WorldSettingEntity entity =
                db.worldSettingDao().getByScriptId(scriptId);
        return entity == null ? null : EntityMapper.toWorld(entity);
    }

    private List<ChatMessage> loadAllMessages(String scriptId) {
        List<MessageEntity> entities = db.messageDao().loadLatest(scriptId, Integer.MAX_VALUE);
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = entities.size() - 1; i >= 0; i--) {
            messages.add(EntityMapper.toMessage(entities.get(i)));
        }
        return messages;
    }

    private ExportWriter.ChatEnvelope buildChatEnvelope(List<ChatMessage> messages) {
        ExportWriter.ChatEnvelope envelope = new ExportWriter.ChatEnvelope();
        envelope.format = "roleplay-chat";
        envelope.schemaVersion = 1;
        envelope.generator = "RolePlayChat/1.0";
        for (ChatMessage m : messages) {
            if (m.getStatus() == ChatMessage.Status.FAILED && m.getContent().isEmpty()) {
                continue;
            }
            ExportWriter.ChatItem item = new ExportWriter.ChatItem();
            item.id = m.getId();
            item.sequence = m.getSequence();
            item.createdAt = m.getCreatedAt();
            item.type = m.getType().name();
            item.senderName = m.getSenderDisplayName();
            item.content = m.getContent();
            item.status = m.getStatus().name();
            envelope.messages.add(item);
        }
        return envelope;
    }

    @Nullable
    private String localizeAvatar(CharacterCardMapper.Avatar avatar) {
        if (avatar == null || avatar.dataBase64 == null || avatar.dataBase64.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(avatar.dataBase64);
            java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(bytes);
            return assetStore.storeStream(LocalAssetStore.DIR_AVATARS, "imported_avatar", ".webp", in);
        } catch (Exception e) {
            return null;
        }
    }

    private CharacterProfile withAvatar(CharacterProfile profile, String avatarRef) {
        return new CharacterProfile(profile.getId(), profile.getScriptId(), profile.getName(),
                profile.getAliases(), avatarRef, profile.getGender(), profile.getAgeText(),
                profile.getPersonality(), profile.getBackstory(), profile.getSpeakingStyle(),
                profile.getCatchphrases(), profile.getStrengths(), profile.getFlaws(),
                profile.getRelationships(), profile.getSampleLines(), profile.getSystemPrompt(),
                profile.getHiddenSetting(), profile.isEnabled(), profile.getSortIndex(),
                profile.getCreatedAt(), profile.getUpdatedAt(), profile.getExtraJson());
    }

    private void setError(@Nullable AppError[] error, String message) {
        if (error != null && error.length > 0) {
            error[0] = AppError.of(AppErrorCode.IMPORT_INVALID, message, false);
        }
    }

    @Nullable
    private AppError getError(@Nullable AppError[] error) {
        return error == null || error.length == 0 ? null : error[0];
    }

    private void logImport(String type, String version, String sourceName, boolean success,
                           @Nullable AppError error) {
        ImportLogEntity log = new ImportLogEntity(
                java.util.UUID.randomUUID().toString(),
                type,
                version,
                sourceName,
                System.currentTimeMillis(),
                success,
                success ? null : (error == null ? AppErrorCode.IMPORT_INVALID.getCode() : error.getCode().getCode()));
        db.importLogDao().insert(log);
    }
}
