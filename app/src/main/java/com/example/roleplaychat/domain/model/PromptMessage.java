package com.example.roleplaychat.domain.model;

import java.util.Objects;

/**
 * Prompt 消息（架构文档 §8.1），对应 OpenAI 的 system/user/assistant 角色。
 */
public final class PromptMessage {

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT
    }

    private final Role role;
    private final String content;

    public PromptMessage(Role role, String content) {
        this.role = Objects.requireNonNull(role);
        this.content = Objects.requireNonNull(content);
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public static PromptMessage system(String content) {
        return new PromptMessage(Role.SYSTEM, content);
    }

    public static PromptMessage user(String content) {
        return new PromptMessage(Role.USER, content);
    }

    public static PromptMessage assistant(String content) {
        return new PromptMessage(Role.ASSISTANT, content);
    }
}
