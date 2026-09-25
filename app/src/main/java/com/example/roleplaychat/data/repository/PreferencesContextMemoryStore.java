package com.example.roleplaychat.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.example.roleplaychat.domain.ai.ContextMemoryStore;
import com.example.roleplaychat.util.JsonUtils;

/** 摘要缓存与聊天数据库分开保存；丢失时可从完整聊天记录重新生成。 */
public final class PreferencesContextMemoryStore implements ContextMemoryStore {

    private final SharedPreferences preferences;

    public PreferencesContextMemoryStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                "conversation_memory_v1", Context.MODE_PRIVATE);
    }

    @Override
    @Nullable
    public Snapshot load(String scriptId) {
        String json = preferences.getString(scriptId, null);
        if (json == null) return null;
        try {
            Snapshot snapshot = JsonUtils.fromJson(json, Snapshot.class);
            return snapshot != null && snapshot.throughMessageId != null
                    && snapshot.summary != null && !snapshot.summary.trim().isEmpty()
                    ? snapshot : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public void save(String scriptId, Snapshot snapshot) {
        preferences.edit().putString(scriptId, JsonUtils.toJson(snapshot)).apply();
    }
}
