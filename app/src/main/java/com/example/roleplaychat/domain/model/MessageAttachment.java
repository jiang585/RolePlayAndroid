package com.example.roleplaychat.domain.model;

import androidx.annotation.Nullable;

/** A media item persisted alongside a chat message. */
public final class MessageAttachment {
    public enum Status { PENDING, READY, FAILED }

    private final String id;
    private final String messageId;
    @Nullable private final String characterId;
    private final String type;
    private final Status status;
    @Nullable private final String localPath;
    @Nullable private final String mimeType;
    private final int width;
    private final int height;
    @Nullable private final String jobId;

    public MessageAttachment(String id, String messageId, @Nullable String characterId, String type,
                             Status status, @Nullable String localPath, @Nullable String mimeType,
                             int width, int height, @Nullable String jobId) {
        this.id = id; this.messageId = messageId; this.characterId = characterId; this.type = type;
        this.status = status; this.localPath = localPath; this.mimeType = mimeType;
        this.width = width; this.height = height; this.jobId = jobId;
    }
    public String getId() { return id; }
    public String getMessageId() { return messageId; }
    @Nullable public String getCharacterId() { return characterId; }
    public String getType() { return type; }
    public Status getStatus() { return status; }
    @Nullable public String getLocalPath() { return localPath; }
    @Nullable public String getMimeType() { return mimeType; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    @Nullable public String getJobId() { return jobId; }
    public boolean isReady() { return status == Status.READY && localPath != null; }
}
