package com.example.roleplaychat.domain.repository;

import androidx.lifecycle.LiveData;

import com.example.roleplaychat.domain.model.AiBatch;
import com.example.roleplaychat.domain.model.ChatMessage;
import com.example.roleplaychat.domain.model.PlayerIdentity;

import java.util.List;

/**
 * 聊天仓库接口（架构文档 §7.1）。
 */
public interface ChatRepository {

    LiveData<List<ChatMessage>> observeLatest(String scriptId, int limit);

    List<ChatMessage> loadBefore(String scriptId, long beforeSequence, int limit);

    /** 按 sequence 正序读取全部消息，仅供生成受限的长期剧情记忆。 */
    List<ChatMessage> loadAll(String scriptId);

    List<ChatMessage> loadAfter(String scriptId, long afterSequence, int limit);

    /** 插入玩家消息并返回消息实体（含分配的 sequence）。senderName/avatarRef 为历史快照。 */
    ChatMessage insertPlayerMessage(PlayerIdentity identity, String content,
                                    String senderName, String avatarRef, String appearanceSnapshotJson, long now);

    /** 插入玩家旁白（居中系统提示，无角色）。 */
    ChatMessage insertNarration(String scriptId, String content, long now);

    /** 事务写入 AI 事件批次，连续分配 sequence。 */
    void insertAiBatch(String scriptId, AiBatch batch, long now);

    void updateStreamingContent(String messageId, String content);

    void markRequestCancelled(String requestId);

    void markRequestFailed(String requestId, String errorCode);

    /** 启动恢复：将所有遗留 STREAMING 标记为 PROCESS_INTERRUPTED。 */
    void markInterruptedStreams();

    void clearMessages(String scriptId);

    long maxSequence(String scriptId);
}
