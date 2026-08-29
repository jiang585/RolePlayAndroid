package com.example.roleplaychat.domain.model;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 玩家身份领域模型（架构文档 §5.2 与 §2.2 规则 1~3）。
 * 一个剧本同一时刻只有一个玩家身份；OBSERVER 时 characterId 为空。
 */
public final class PlayerIdentity {

    public enum RoleType {
        /** 主角：绑定一个角色作为我的代表身份。 */
        PROTAGONIST,
        /** 配角。 */
        SUPPORTING,
        /** 旁观者/叙述视角，不绑定角色。 */
        OBSERVER
    }

    private final String scriptId;
    private final RoleType roleType;
    @Nullable
    private final String characterId;
    private final long changedAt;

    public PlayerIdentity(String scriptId, RoleType roleType, @Nullable String characterId, long changedAt) {
        this.scriptId = Objects.requireNonNull(scriptId);
        this.roleType = Objects.requireNonNull(roleType);
        this.characterId = characterId;
        this.changedAt = changedAt;
    }

    public String getScriptId() {
        return scriptId;
    }

    public RoleType getRoleType() {
        return roleType;
    }

    @Nullable
    public String getCharacterId() {
        return characterId;
    }

    public long getChangedAt() {
        return changedAt;
    }

    public boolean isObserver() {
        return roleType == RoleType.OBSERVER;
    }
}
