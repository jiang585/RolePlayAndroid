package com.example.roleplaychat.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.example.roleplaychat.data.local.AppDatabase;
import com.example.roleplaychat.data.local.dao.MessageDao;
import com.example.roleplaychat.data.local.entity.MessageEntity;
import com.example.roleplaychat.data.mapper.EntityMapper;
import com.example.roleplaychat.domain.model.AiBatch;
import com.example.roleplaychat.domain.model.AiEvent;
import com.example.roleplaychat.domain.model.ChatMessage;
import com.example.roleplaychat.domain.model.PlayerIdentity;
import com.example.roleplaychat.domain.repository.ChatRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 聊天仓库实现（架构文档 §6.2/§7.1）。
 */
public class ChatRepositoryImpl implements ChatRepository {

    private final AppDatabase db;
    private final MessageDao dao;

    public ChatRepositoryImpl(AppDatabase db) {
        this.db = db;
        this.dao = db.messageDao();
    }

    @Override
    public LiveData<List<ChatMessage>> observeLatest(String scriptId, int limit) {
        return Transformations.map(dao.observeLatest(scriptId, limit), entities -> {
            // 倒序读取，UI 需正序展示，因此这里反转为正序
            List<ChatMessage> result = new ArrayList<>(entities.size());
            for (int i = entities.size() - 1; i >= 0; i--) {
                result.add(EntityMapper.toMessage(entities.get(i)));
            }
            return result;
        });
    }

    @Override
    public List<ChatMessage> loadBefore(String scriptId, long beforeSequence, int limit) {
        List<MessageEntity> entities = dao.loadBefore(scriptId, beforeSequence, limit);
        List<ChatMessage> result = new ArrayList<>(entities.size());
        for (int i = entities.size() - 1; i >= 0; i--) {
            result.add(EntityMapper.toMessage(entities.get(i)));
        }
        return result;
    }

    @Override
    public List<ChatMessage> loadAll(String scriptId) {
        return dao.loadAll(scriptId).stream()
                .map(EntityMapper::toMessage)
                .collect(Collectors.toList());
    }

    @Override
    public List<ChatMessage> loadAfter(String scriptId, long afterSequence, int limit) {
        return dao.loadAfter(scriptId, afterSequence, limit).stream()
                .map(EntityMapper::toMessage)
                .collect(Collectors.toList());
    }

    @Override
    public ChatMessage insertPlayerMessage(PlayerIdentity identity, String content,
                                           String senderName, String avatarRef,
                                           String appearanceSnapshotJson, long now) {
        String messageId = java.util.UUID.randomUUID().toString();
        MessageEntity entity = new MessageEntity(
                messageId,
                identity.getScriptId(),
                identity.getCharacterId(),
                senderName,
                avatarRef,
                appearanceSnapshotJson,
                identity.getRoleType().name(),
                ChatMessage.Type.CHARACTER_TEXT.name(),
                ChatMessage.Side.MINE.name(),
                content,
                0,
                now,
                ChatMessage.Status.DONE.name(),
                null,
                null,
                null,
                null,
                null);
        long sequence = dao.insertPlayerMessageTx(entity);
        return EntityMapper.toMessage(entity);
    }

    @Override
    public ChatMessage insertNarration(String scriptId, String content, long now) {
        String messageId = java.util.UUID.randomUUID().toString();
        MessageEntity entity = new MessageEntity(
                messageId,
                scriptId,
                null,
                null,
                null,
                null,
                null,
                ChatMessage.Type.NARRATION.name(),
                ChatMessage.Side.CENTER.name(),
                content,
                0,
                now,
                ChatMessage.Status.DONE.name(),
                null,
                null,
                null,
                null,
                null);
        dao.insertPlayerMessageTx(entity);
        return EntityMapper.toMessage(entity);
    }

    @Override
    public void insertAiBatch(String scriptId, AiBatch batch, long now) {
        List<MessageEntity> entities = new ArrayList<>();
        int turn = 0;
        for (AiEvent event : batch.getEvents()) {
            String senderName = null;
            ChatMessage.Type type;
            ChatMessage.Side side;
            String characterId = null;
            switch (event.getType()) {
                case NARRATION:
                    type = ChatMessage.Type.NARRATION;
                    side = ChatMessage.Side.CENTER;
                    break;
                case SYSTEM_EVENT:
                    type = ChatMessage.Type.SYSTEM_EVENT;
                    side = ChatMessage.Side.CENTER;
                    break;
                case CHARACTER_TURN:
                default:
                    type = ChatMessage.Type.CHARACTER_TEXT;
                    side = ChatMessage.Side.THEIRS;
                    characterId = event.getCharacterId();
                    break;
            }
            if (characterId != null) {
                com.example.roleplaychat.data.local.entity.CharacterEntity character =
                        db.characterDao().getById(characterId);
                com.example.roleplaychat.domain.model.CharacterProfile profile = character == null
                        ? null : EntityMapper.toProfile(character);
                if (profile != null) {
                    senderName = profile.getName();
                    // 角色头像必须按消息快照保存，历史消息不随角色编辑变化。
                    entities.add(new MessageEntity(
                            event.getEventId(), scriptId, characterId, senderName,
                            profile.getAvatarRef(), null, null, type.name(), side.name(),
                            event.getContent(), 0, now, ChatMessage.Status.DONE.name(),
                            batch.getRequestId(), batch.getRequestId(), turn++, null, null));
                    continue;
                }
            }
            MessageEntity entity = new MessageEntity(
                    event.getEventId(),
                    scriptId,
                    characterId,
                    senderName,
                    null,
                    null,
                    null,
                    type.name(),
                    side.name(),
                    event.getContent(),
                    0,
                    now,
                    ChatMessage.Status.DONE.name(),
                    batch.getRequestId(),
                    batch.getRequestId(),
                    turn++,
                    null,
                    null);
            entities.add(entity);
        }
        if (!entities.isEmpty()) {
            db.runInTransaction(() -> dao.insertAiBatchTx(entities));
        }
    }

    @Override
    public void updateStreamingContent(String messageId, String content) {
        db.runInTransaction(() -> dao.updateContent(messageId, content));
    }

    @Override
    public void markRequestCancelled(String requestId) {
        db.runInTransaction(() -> dao.markRequest(requestId, ChatMessage.Status.CANCELLED.name()));
    }

    @Override
    public void markRequestFailed(String requestId, String errorCode) {
        db.runInTransaction(() -> dao.markRequestFailed(requestId,
                ChatMessage.Status.FAILED.name(), errorCode));
    }

    @Override
    public void markInterruptedStreams() {
        db.runInTransaction(dao::markInterruptedStreams);
    }

    @Override
    public void clearMessages(String scriptId) {
        db.runInTransaction(() -> dao.clearByScript(scriptId));
    }

    @Override
    public long maxSequence(String scriptId) {
        return dao.maxSequence(scriptId);
    }
}
