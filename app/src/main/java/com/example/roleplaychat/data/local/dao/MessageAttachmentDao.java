package com.example.roleplaychat.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.roleplaychat.data.local.entity.MessageAttachmentEntity;

import java.util.List;

@Dao
public interface MessageAttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(MessageAttachmentEntity entity);

    @Update
    int update(MessageAttachmentEntity entity);

    @Query("SELECT * FROM message_attachments WHERE message_id = :messageId ORDER BY sort_index ASC")
    List<MessageAttachmentEntity> getByMessageId(String messageId);

    @Query("SELECT * FROM message_attachments WHERE job_id = :jobId LIMIT 1")
    MessageAttachmentEntity getByJobId(String jobId);

    @Query("SELECT * FROM message_attachments WHERE message_id = :messageId ORDER BY sort_index ASC")
    List<MessageAttachmentEntity> getForMessage(String messageId);

    @Query("SELECT * FROM message_attachments")
    List<MessageAttachmentEntity> getAll();
}
