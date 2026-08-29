package com.example.roleplaychat.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.example.roleplaychat.data.local.entity.MessageEntity;

import java.util.List;

/**
 * 消息 DAO（架构文档 §6.2 messages）。
 * sequence 在事务中分配，批量插入连续分配。
 */
@Dao
public interface MessageDao {

    @Query("SELECT * FROM messages WHERE script_id = :scriptId ORDER BY sequence DESC LIMIT :limit")
    List<MessageEntity> loadLatest(String scriptId, int limit);

    @Query("SELECT * FROM messages WHERE script_id = :scriptId ORDER BY sequence DESC LIMIT :limit")
    LiveData<List<MessageEntity>> observeLatest(String scriptId, int limit);

    @Query("SELECT * FROM messages WHERE script_id = :scriptId AND sequence < :beforeSequence " +
            "ORDER BY sequence DESC LIMIT :limit")
    List<MessageEntity> loadBefore(String scriptId, long beforeSequence, int limit);

    /**
     * 供 AI 上下文构建长期剧情记忆使用。调用方负责做字符预算，不能直接拼入 Prompt。
     */
    @Query("SELECT * FROM messages WHERE script_id = :scriptId ORDER BY sequence ASC")
    List<MessageEntity> loadAll(String scriptId);

    @Query("SELECT * FROM messages WHERE script_id = :scriptId AND sequence > :afterSequence " +
            "ORDER BY sequence ASC LIMIT :limit")
    List<MessageEntity> loadAfter(String scriptId, long afterSequence, int limit);

    @Query("SELECT COALESCE(MAX(sequence), 0) FROM messages WHERE script_id = :scriptId")
    long maxSequence(String scriptId);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(MessageEntity entity);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    List<Long> insertAll(List<MessageEntity> entities);

    @Query("UPDATE messages SET content = :content WHERE id = :id")
    int updateContent(String id, String content);

    @Query("UPDATE messages SET status = :status WHERE request_id = :requestId AND status = 'STREAMING'")
    int markRequest(String requestId, String status);

    @Query("UPDATE messages SET status = :status, error_code = :errorCode WHERE request_id = :requestId AND status = 'STREAMING'")
    int markRequestFailed(String requestId, String status, String errorCode);

    @Query("UPDATE messages SET status = 'PROCESS_INTERRUPTED' WHERE status = 'STREAMING'")
    int markInterruptedStreams();

    @Query("DELETE FROM messages WHERE script_id = :scriptId")
    int clearByScript(String scriptId);

    @Query("SELECT COUNT(*) FROM messages WHERE script_id = :scriptId")
    int countByScript(String scriptId);

    @Query("SELECT COUNT(*) FROM messages WHERE request_id = :requestId AND status = 'DONE'")
    int countDoneByRequestId(String requestId);

    @Query("SELECT COUNT(*) FROM messages WHERE id = :id")
    int countById(String id);

    /** 在事务内完成玩家消息插入与 sequence 分配。 */
    @Transaction
    default long insertPlayerMessageTx(MessageEntity entity) {
        long next = maxSequence(entity.script_id) + 1;
        entity.sequence = next;
        insert(entity);
        return next;
    }

    /** 在事务内批量插入 AI 事件，连续分配 sequence。 */
    @Transaction
    default void insertAiBatchTx(List<MessageEntity> entities) {
        if (entities.isEmpty()) {
            return;
        }
        List<MessageEntity> pending = new java.util.ArrayList<>();
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (MessageEntity entity : entities) {
            if (!ids.add(entity.id) || countById(entity.id) > 0) {
                continue;
            }
            pending.add(entity);
        }
        if (pending.isEmpty()) {
            return;
        }
        long next = maxSequence(pending.get(0).script_id) + 1;
        for (MessageEntity entity : pending) {
            entity.sequence = next++;
        }
        insertAll(pending);
    }
}
