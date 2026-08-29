package com.example.roleplaychat.domain.model;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 装扮领域模型（架构文档 §6.2 appearances）。
 * 解析优先级：角色覆盖 > 剧本设置 > 全局设置 > 应用默认。
 */
public final class Appearance {

    public enum ScopeType {
        GLOBAL,
        SCRIPT,
        CHARACTER
    }

    public enum BackgroundType {
        BUILTIN,
        IMAGE
    }

    public enum BackgroundMode {
        FIT_CENTER,
        CENTER_CROP,
        TILE
    }

    private final String id;
    private final ScopeType scopeType;
    /** GLOBAL 时为固定值 {@code "global"}，其他为实体 ID。 */
    private final String scopeId;
    private final BackgroundType backgroundType;
    @Nullable
    private final String backgroundRef;
    private final BackgroundMode backgroundMode;
    private final float backgroundDimAlpha;
    private final String bubbleStyleId;
    private final String bubbleColor;
    private final String textColor;
    private final String nicknameColor;
    private final float fontScale;

    public Appearance(String id, ScopeType scopeType, String scopeId,
                      BackgroundType backgroundType, @Nullable String backgroundRef,
                      BackgroundMode backgroundMode, float backgroundDimAlpha,
                      String bubbleStyleId, String bubbleColor, String textColor,
                      String nicknameColor, float fontScale) {
        this.id = Objects.requireNonNull(id);
        this.scopeType = Objects.requireNonNull(scopeType);
        this.scopeId = Objects.requireNonNull(scopeId);
        this.backgroundType = backgroundType == null ? BackgroundType.BUILTIN : backgroundType;
        this.backgroundRef = backgroundRef;
        this.backgroundMode = backgroundMode == null ? BackgroundMode.CENTER_CROP : backgroundMode;
        this.backgroundDimAlpha = backgroundDimAlpha;
        this.bubbleStyleId = bubbleStyleId;
        this.bubbleColor = bubbleColor;
        this.textColor = textColor;
        this.nicknameColor = nicknameColor;
        this.fontScale = fontScale;
    }

    public String getId() {
        return id;
    }

    public ScopeType getScopeType() {
        return scopeType;
    }

    public String getScopeId() {
        return scopeId;
    }

    public BackgroundType getBackgroundType() {
        return backgroundType;
    }

    @Nullable
    public String getBackgroundRef() {
        return backgroundRef;
    }

    public BackgroundMode getBackgroundMode() {
        return backgroundMode;
    }

    public float getBackgroundDimAlpha() {
        return backgroundDimAlpha;
    }

    public String getBubbleStyleId() {
        return bubbleStyleId;
    }

    public String getBubbleColor() {
        return bubbleColor;
    }

    public String getTextColor() {
        return textColor;
    }

    public String getNicknameColor() {
        return nicknameColor;
    }

    public float getFontScale() {
        return fontScale;
    }

    public Appearance copyWith(BackgroundType backgroundType, @Nullable String backgroundRef,
                               BackgroundMode backgroundMode, float backgroundDimAlpha,
                               String bubbleStyleId, String bubbleColor, String textColor,
                               String nicknameColor, float fontScale) {
        return new Appearance(id, scopeType, scopeId, backgroundType, backgroundRef,
                backgroundMode, backgroundDimAlpha, bubbleStyleId, bubbleColor,
                textColor, nicknameColor, fontScale);
    }
}
