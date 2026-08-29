package com.example.roleplaychat.ui.chat;

import androidx.annotation.Nullable;

import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.Appearance;
import com.example.roleplaychat.domain.model.PlayerIdentity;

import java.util.List;

/**
 * 聊天列表项：消息或日期分隔（架构文档 §10.2/§10.3）。
 */
public final class ChatListItem {

    public enum Kind {
        MESSAGE,
        DATE_SEPARATOR
    }

    private final Kind kind;
    private final com.example.roleplaychat.domain.model.ChatMessage message;
    private final String dateLabel;

    private ChatListItem(Kind kind, com.example.roleplaychat.domain.model.ChatMessage message, String dateLabel) {
        this.kind = kind;
        this.message = message;
        this.dateLabel = dateLabel;
    }

    public static ChatListItem message(com.example.roleplaychat.domain.model.ChatMessage message) {
        return new ChatListItem(Kind.MESSAGE, message, null);
    }

    public static ChatListItem dateSeparator(String label) {
        return new ChatListItem(Kind.DATE_SEPARATOR, null, label);
    }

    public Kind getKind() {
        return kind;
    }

    @Nullable
    public com.example.roleplaychat.domain.model.ChatMessage getMessage() {
        return message;
    }

    @Nullable
    public String getDateLabel() {
        return dateLabel;
    }
}
