package com.example.roleplaychat.domain.ai;

import androidx.annotation.Nullable;

/** 已压缩剧情的持久化快照。消息 ID 用来防止清空或导入聊天后误用旧摘要。 */
public interface ContextMemoryStore {

    final class Snapshot {
        public final String throughMessageId;
        public final String summary;

        public Snapshot(String throughMessageId, String summary) {
            this.throughMessageId = throughMessageId;
            this.summary = summary;
        }
    }

    @Nullable
    Snapshot load(String scriptId);

    void save(String scriptId, Snapshot snapshot);
}
