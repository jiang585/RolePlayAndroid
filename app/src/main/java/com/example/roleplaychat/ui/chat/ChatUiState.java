package com.example.roleplaychat.ui.chat;

import androidx.annotation.Nullable;

import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.Appearance;
import com.example.roleplaychat.domain.model.PlayerIdentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聊天页 UI 状态（架构文档 §10.2 标准）。
 */
public final class ChatUiState {

    private final boolean initialLoading;
    private final boolean loadingEarlier;
    private final boolean generating;
    private final String activeRequestId;
    private final List<ChatListItem> items;
    private final PlayerIdentity identity;
    private final String draft;
    private final Appearance appearance;
    private final AppError error;

    private ChatUiState(Builder b) {
        this.initialLoading = b.initialLoading;
        this.loadingEarlier = b.loadingEarlier;
        this.generating = b.generating;
        this.activeRequestId = b.activeRequestId;
        this.items = b.items == null ? new ArrayList<>() : b.items;
        this.identity = b.identity;
        this.draft = b.draft == null ? "" : b.draft;
        this.appearance = b.appearance;
        this.error = b.error;
    }

    public boolean isInitialLoading() {
        return initialLoading;
    }

    public boolean isLoadingEarlier() {
        return loadingEarlier;
    }

    public boolean isGenerating() {
        return generating;
    }

    @Nullable
    public String getActiveRequestId() {
        return activeRequestId;
    }

    public List<ChatListItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    @Nullable
    public PlayerIdentity getIdentity() {
        return identity;
    }

    public String getDraft() {
        return draft;
    }

    @Nullable
    public Appearance getAppearance() {
        return appearance;
    }

    @Nullable
    public AppError getError() {
        return error;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean initialLoading = true;
        private boolean loadingEarlier;
        private boolean generating;
        private String activeRequestId;
        private List<ChatListItem> items;
        private PlayerIdentity identity;
        private String draft;
        private Appearance appearance;
        private AppError error;

        public Builder initialLoading(boolean v) {
            initialLoading = v;
            return this;
        }

        public Builder loadingEarlier(boolean v) {
            loadingEarlier = v;
            return this;
        }

        public Builder generating(boolean v) {
            generating = v;
            return this;
        }

        public Builder activeRequestId(@Nullable String v) {
            activeRequestId = v;
            return this;
        }

        public Builder items(List<ChatListItem> v) {
            items = v;
            return this;
        }

        public Builder identity(@Nullable PlayerIdentity v) {
            identity = v;
            return this;
        }

        public Builder draft(String v) {
            draft = v;
            return this;
        }

        public Builder appearance(@Nullable Appearance v) {
            appearance = v;
            return this;
        }

        public Builder error(@Nullable AppError v) {
            error = v;
            return this;
        }

        public ChatUiState build() {
            return new ChatUiState(this);
        }
    }
}
