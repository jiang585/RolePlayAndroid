package com.example.roleplaychat.data.repository;

import com.example.roleplaychat.data.local.AppDatabase;
import com.example.roleplaychat.data.local.entity.CharacterVisualAssetEntity;
import com.example.roleplaychat.data.local.entity.CharacterVisualProfileEntity;
import com.example.roleplaychat.domain.model.CharacterVisualAsset;
import com.example.roleplaychat.domain.model.CharacterVisualProfile;
import com.example.roleplaychat.domain.repository.CharacterVisualRepository;

import java.util.ArrayList;
import java.util.List;

public final class CharacterVisualRepositoryImpl implements CharacterVisualRepository {
    private final AppDatabase db;

    public CharacterVisualRepositoryImpl(AppDatabase db) { this.db = db; }

    @Override public CharacterVisualProfile getByCharacterId(String characterId) {
        CharacterVisualProfileEntity e = db.characterVisualProfileDao().getByCharacterId(characterId);
        if (e == null) return null;
        List<CharacterVisualAsset> assets = new ArrayList<>();
        for (CharacterVisualAssetEntity a : db.characterVisualAssetDao().getByProfileId(e.id)) {
            assets.add(new CharacterVisualAsset(a.id, a.profile_id, a.local_path, a.sha256,
                    a.asset_type, a.is_primary, a.width, a.height, a.created_at));
        }
        return new CharacterVisualProfile(e.id, e.character_id, parseStatus(e.status), parseSource(e.source), e.version,
                e.identity_prompt, e.appearance_json, e.negative_prompt, e.created_at, e.updated_at, assets);
    }

    @Override public void save(CharacterVisualProfile profile) {
        db.runInTransaction(() -> {
            db.characterVisualProfileDao().upsert(new CharacterVisualProfileEntity(profile.getId(), profile.getCharacterId(),
                    profile.getStatus().name(), profile.getSource().name(), profile.getVersion(), profile.getIdentityPrompt(),
                    profile.getAppearanceJson(), profile.getNegativePrompt(), profile.getCreatedAt(), profile.getUpdatedAt()));
            db.characterVisualAssetDao().deleteByProfileId(profile.getId());
            for (CharacterVisualAsset a : profile.getAssets()) {
                db.characterVisualAssetDao().insert(new CharacterVisualAssetEntity(a.getId(), a.getProfileId(), a.getLocalPath(),
                        a.getSha256(), a.getAssetType(), a.isPrimary(), a.getWidth(), a.getHeight(), a.getCreatedAt()));
            }
        });
    }

    @Override public void deleteByCharacterId(String characterId) {
        db.runInTransaction(() -> db.characterVisualProfileDao().deleteByCharacterId(characterId));
    }

    private static CharacterVisualProfile.Status parseStatus(String value) {
        try { return CharacterVisualProfile.Status.valueOf(value); } catch (Exception ignored) { return CharacterVisualProfile.Status.NEEDS_SETUP; }
    }
    private static CharacterVisualProfile.Source parseSource(String value) {
        try { return CharacterVisualProfile.Source.valueOf(value); } catch (Exception ignored) { return CharacterVisualProfile.Source.UPLOAD; }
    }
}
