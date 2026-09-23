package com.example.roleplaychat.data.local.entity;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.List;

public final class MessageWithAttachments {
    @Embedded public MessageEntity message;
    @Relation(parentColumn = "id", entityColumn = "message_id")
    public List<MessageAttachmentEntity> attachments;
}
