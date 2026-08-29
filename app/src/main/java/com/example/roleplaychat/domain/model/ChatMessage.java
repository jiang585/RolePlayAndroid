package com.example.roleplaychat.domain.model;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 聊天消息领域模型（架构文档 §5.2）。不可变。
 * 发送者名称与头像为发送时的快照，历史显示不随角色修改而变化（规则 2）。
 */
public final class ChatMessage {

    public enum Type {
        /** 角色发言（气泡）。 */
        CHARACTER_TEXT,
        /** 旁白/动作/表情（居中系统提示，无气泡）。 */
        NARRATION,
        /** 系统事件（进群/退群/公告等）。 */
        SYSTEM_EVENT
    }

    public enum Side {
        /** 我（右侧）。 */
        MINE,
        /** AI/NPC（左侧）。 */
        THEIRS,
        /** 居中（旁白/事件）。 */
        CENTER
    }

    public enum Status {
        STREAMING,
        DONE,
        CANCELLED,
        FAILED,
        /** 进程被中断遗留（启动恢复时标记）。 */
        PROCESS_INTERRUPTED
    }

    private final String id;
    private final String scriptId;
    @Nullable
    private final String characterId;
    @Nullable
    private final String senderDisplayName;
    @Nullable
    private final String senderAvatarRef;
    @Nullable
    private final String appearanceSnapshotJson;
    @Nullable
    private final PlayerIdentity.RoleType playerRoleType;
    private final Type type;
    private final Side side;
    private final String content;
    private final long sequence;
    private final long createdAt;
    private final Status status;
    @Nullable
    private final String requestId;
    @Nullable
    private final String batchId;
    @Nullable
    private final Integer turnIndex;
    @Nullable
    private final String errorCode;
    @Nullable
    private final String metaJson;

    private ChatMessage(Builder b) {
        this.id = Objects.requireNonNull(b.id);
        this.scriptId = Objects.requireNonNull(b.scriptId);
        this.characterId = b.characterId;
        this.senderDisplayName = b.senderDisplayName;
        this.senderAvatarRef = b.senderAvatarRef;
        this.appearanceSnapshotJson = b.appearanceSnapshotJson;
        this.playerRoleType = b.playerRoleType;
        this.type = Objects.requireNonNull(b.type);
        this.side = Objects.requireNonNull(b.side);
        this.content = b.content == null ? "" : b.content;
        this.sequence = b.sequence;
        this.createdAt = b.createdAt;
        this.status = b.status == null ? Status.DONE : b.status;
        this.requestId = b.requestId;
        this.batchId = b.batchId;
        this.turnIndex = b.turnIndex;
        this.errorCode = b.errorCode;
        this.metaJson = b.metaJson;
    }

    public String getId() {
        return id;
    }

    public String getScriptId() {
        return scriptId;
    }

    @Nullable
    public String getCharacterId() {
        return characterId;
    }

    @Nullable
    public String getSenderDisplayName() {
        return senderDisplayName;
    }

    @Nullable
    public String getSenderAvatarRef() {
        return senderAvatarRef;
    }

    @Nullable
    public String getAppearanceSnapshotJson() {
        return appearanceSnapshotJson;
    }

    @Nullable
    public PlayerIdentity.RoleType getPlayerRoleType() {
        return playerRoleType;
    }

    public Type getType() {
        return type;
    }

    public Side getSide() {
        return side;
    }

    public String getContent() {
        return content;
    }

    public long getSequence() {
        return sequence;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Status getStatus() {
        return status;
    }

    @Nullable
    public String getRequestId() {
        return requestId;
    }

    @Nullable
    public String getBatchId() {
        return batchId;
    }

    @Nullable
    public Integer getTurnIndex() {
        return turnIndex;
    }

    @Nullable
    public String getErrorCode() {
        return errorCode;
    }

    @Nullable
    public String getMetaJson() {
        return metaJson;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String scriptId;
        private String characterId;
        private String senderDisplayName;
        private String senderAvatarRef;
        private String appearanceSnapshotJson;
        private PlayerIdentity.RoleType playerRoleType;
        private Type type;
        private Side side;
        private String content;
        private long sequence;
        private long createdAt;
        private Status status;
        private String requestId;
        private String batchId;
        private Integer turnIndex;
        private String errorCode;
        private String metaJson;

        public Builder id(String v) {
            id = v;
            return this;
        }

        public Builder scriptId(String v) {
            scriptId = v;
            return this;
        }

        public Builder characterId(@Nullable String v) {
            characterId = v;
            return this;
        }

        public Builder senderDisplayName(@Nullable String v) {
            senderDisplayName = v;
            return this;
        }

        public Builder senderAvatarRef(@Nullable String v) {
            senderAvatarRef = v;
            return this;
        }

        public Builder appearanceSnapshotJson(@Nullable String v) {
            appearanceSnapshotJson = v;
            return this;
        }

        public Builder playerRoleType(@Nullable PlayerIdentity.RoleType v) {
            playerRoleType = v;
            return this;
        }

        public Builder type(Type v) {
            type = v;
            return this;
        }

        public Builder side(Side v) {
            side = v;
            return this;
        }

        public Builder content(String v) {
            content = v;
            return this;
        }

        public Builder sequence(long v) {
            sequence = v;
            return this;
        }

        public Builder createdAt(long v) {
            createdAt = v;
            return this;
        }

        public Builder status(Status v) {
            status = v;
            return this;
        }

        public Builder requestId(@Nullable String v) {
            requestId = v;
            return this;
        }

        public Builder batchId(@Nullable String v) {
            batchId = v;
            return this;
        }

        public Builder turnIndex(@Nullable Integer v) {
            turnIndex = v;
            return this;
        }

        public Builder errorCode(@Nullable String v) {
            errorCode = v;
            return this;
        }

        public Builder metaJson(@Nullable String v) {
            metaJson = v;
            return this;
        }

        public ChatMessage build() {
            return new ChatMessage(this);
        }
    }
}
