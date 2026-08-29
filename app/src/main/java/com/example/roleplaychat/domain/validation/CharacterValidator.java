package com.example.roleplaychat.domain.validation;

import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.AppErrorCode;
import com.example.roleplaychat.domain.model.CharacterProfile;

/**
 * 角色校验（架构文档 §2.3/§14.1）：名称必填且有限长，文本字段限长。
 */
public final class CharacterValidator {

    public static final int MAX_NAME = 80;
    public static final int MAX_TEXT = 4096;

    private CharacterValidator() {
    }

    public static AppError validateProfile(CharacterProfile profile) {
        if (profile == null) {
            return AppError.of(AppErrorCode.VALIDATION_FAILED, "profile required", false);
        }
        if (profile.getName() == null || profile.getName().trim().isEmpty()) {
            return AppError.of(AppErrorCode.VALIDATION_FAILED, "name required", false);
        }
        if (profile.getName().trim().length() > MAX_NAME) {
            return AppError.of(AppErrorCode.VALIDATION_FAILED, "name too long", false);
        }
        if (tooLong(profile.getBackstory()) || tooLong(profile.getPersonality())
                || tooLong(profile.getSpeakingStyle()) || tooLong(profile.getSystemPrompt())
                || tooLong(profile.getHiddenSetting())) {
            return AppError.of(AppErrorCode.VALIDATION_FAILED, "field too long", false);
        }
        return null;
    }

    private static boolean tooLong(String value) {
        return value != null && value.length() > MAX_TEXT;
    }
}
