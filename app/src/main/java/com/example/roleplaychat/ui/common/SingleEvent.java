package com.example.roleplaychat.ui.common;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 一次性事件（架构文档 §10.2）：Toast、导航、文件选择等。
 * 只消费一次，避免旋转屏幕后重复触发。
 */
public final class SingleEvent<T> {

    private T content;
    private boolean consumed;

    public SingleEvent(@Nullable T content) {
        this.content = content;
    }

    @Nullable
    public T getContentIfNotHandled() {
        if (consumed) {
            return null;
        }
        consumed = true;
        return content;
    }

    @Nullable
    public T peekContent() {
        return content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SingleEvent)) {
            return false;
        }
        SingleEvent<?> that = (SingleEvent<?>) o;
        return consumed == that.consumed && Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, consumed);
    }
}
