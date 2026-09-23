package com.example.roleplaychat.domain.repository;

import androidx.annotation.Nullable;

import com.example.roleplaychat.domain.model.CharacterVisualProfile;

public interface CharacterVisualRepository {
    @Nullable CharacterVisualProfile getByCharacterId(String characterId);
    void save(CharacterVisualProfile profile);
    void deleteByCharacterId(String characterId);
}
