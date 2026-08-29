package com.example.roleplaychat.domain.usecase;

import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.repository.CharacterRepository;
import com.example.roleplaychat.domain.validation.CharacterValidator;

/**
 * 保存角色用例（架构文档 §7.2）。
 */
public class SaveCharacterUseCase {

    private final CharacterRepository characterRepository;

    public SaveCharacterUseCase(CharacterRepository characterRepository) {
        this.characterRepository = characterRepository;
    }

    /** @return null 表示成功；非 null 为校验错误。 */
    public AppError execute(CharacterProfile profile) {
        AppError error = CharacterValidator.validateProfile(profile);
        if (error != null) {
            return error;
        }
        characterRepository.save(profile);
        return null;
    }
}
