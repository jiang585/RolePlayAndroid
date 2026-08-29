package com.example.roleplaychat.domain.usecase;

import androidx.annotation.Nullable;

import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.model.PlayerIdentity;
import com.example.roleplaychat.domain.repository.CharacterRepository;
import com.example.roleplaychat.domain.repository.ScriptRepository;

/**
 * 切换身份用例（架构文档 §2.2 规则 1~3、§7.2）：
 * 校验角色启用；切换后该角色被排除出 AI 可编排 NPC 列表（由编排器保证）。
 * 身份切换仅影响后续消息。
 */
public class SwitchIdentityUseCase {

    private final ScriptRepository scriptRepository;
    private final CharacterRepository characterRepository;

    public SwitchIdentityUseCase(ScriptRepository scriptRepository,
                                 CharacterRepository characterRepository) {
        this.scriptRepository = scriptRepository;
        this.characterRepository = characterRepository;
    }

    /** @return 新身份，或 null 表示校验失败。 */
    @Nullable
    public PlayerIdentity execute(String scriptId, PlayerIdentity.RoleType roleType,
                                  @Nullable String characterId, long now, @Nullable AppError[] error) {
        if (roleType == null) {
            setError(error, AppError.of(com.example.roleplaychat.domain.model.AppErrorCode.VALIDATION_FAILED,
                    "role type required", false));
            return null;
        }
        if (roleType == PlayerIdentity.RoleType.OBSERVER) {
            characterId = null;
        } else {
            if (characterId == null) {
                setError(error, AppError.of(com.example.roleplaychat.domain.model.AppErrorCode.VALIDATION_FAILED,
                        "character required", false));
                return null;
            }
            CharacterProfile profile = characterRepository.getById(characterId);
            if (profile == null || !profile.isEnabled()) {
                setError(error, AppError.of(com.example.roleplaychat.domain.model.AppErrorCode.VALIDATION_FAILED,
                        "character not enabled", false));
                return null;
            }
        }
        PlayerIdentity identity = new PlayerIdentity(scriptId, roleType, characterId, now);
        scriptRepository.setPlayerIdentity(identity);
        return identity;
    }

    private void setError(@Nullable AppError[] error, AppError appError) {
        if (error != null && error.length > 0) {
            error[0] = appError;
        }
    }
}
