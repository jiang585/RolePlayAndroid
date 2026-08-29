package com.example.roleplaychat.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 装扮表（架构文档 §6.2 appearances）。唯一索引 (scope_type, scope_id)。
 */
@Entity(tableName = "appearances",
        indices = {@Index(value = {"scope_type", "scope_id"}, unique = true)})
public class AppearanceEntity {

    public static final String SCOPE_GLOBAL = "GLOBAL";
    public static final String SCOPE_SCRIPT = "SCRIPT";
    public static final String SCOPE_CHARACTER = "CHARACTER";

    public static final String BG_BUILTIN = "BUILTIN";
    public static final String BG_IMAGE = "IMAGE";

    public static final String BG_MODE_FIT_CENTER = "FIT_CENTER";
    public static final String BG_MODE_CENTER_CROP = "CENTER_CROP";
    public static final String BG_MODE_TILE = "TILE";

    @NonNull
    @PrimaryKey
    public String id;

    public String scope_type;

    public String scope_id;

    public String background_type;

    @Nullable
    public String background_ref;

    public String background_mode;

    public float background_dim_alpha;

    public String bubble_style_id;

    public String bubble_color;

    public String text_color;

    public String nickname_color;

    public float font_scale;

    public AppearanceEntity() {
    }

    public AppearanceEntity(String id, String scopeType, String scopeId,
                            String backgroundType, @Nullable String backgroundRef,
                            String backgroundMode, float backgroundDimAlpha,
                            String bubbleStyleId, String bubbleColor, String textColor,
                            String nicknameColor, float fontScale) {
        this.id = id;
        this.scope_type = scopeType;
        this.scope_id = scopeId;
        this.background_type = backgroundType;
        this.background_ref = backgroundRef;
        this.background_mode = backgroundMode;
        this.background_dim_alpha = backgroundDimAlpha;
        this.bubble_style_id = bubbleStyleId;
        this.bubble_color = bubbleColor;
        this.text_color = textColor;
        this.nickname_color = nicknameColor;
        this.font_scale = fontScale;
    }
}
