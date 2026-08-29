package com.example.roleplaychat.data.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Transformations;

import com.example.roleplaychat.data.local.AppDatabase;
import com.example.roleplaychat.data.local.dao.AppearanceDao;
import com.example.roleplaychat.data.local.entity.AppearanceEntity;
import com.example.roleplaychat.data.mapper.EntityMapper;
import com.example.roleplaychat.domain.model.Appearance;
import com.example.roleplaychat.domain.repository.AppearanceRepository;

/**
 * 装扮仓库实现（架构文档 §6.2）：解析优先级 角色 > 剧本 > 全局 > 默认。
 */
public class AppearanceRepositoryImpl implements AppearanceRepository {

    private final AppDatabase db;
    private final AppearanceDao dao;

    public AppearanceRepositoryImpl(AppDatabase db) {
        this.db = db;
        this.dao = db.appearanceDao();
    }

    @Override
    public LiveData<Appearance> observeEffective(String scriptId, @Nullable String characterId) {
        MediatorLiveData<Appearance> result = new MediatorLiveData<>();
        LiveData<AppearanceEntity> script = dao.observeScript(scriptId);
        LiveData<AppearanceEntity> character = characterId == null ? null : dao.observeCharacter(characterId);
        LiveData<AppearanceEntity> global = dao.observeByScope(Appearance.ScopeType.GLOBAL.name(), "global");

        Runnable recompute = () -> {
            AppearanceEntity characterEntity = character == null ? null : character.getValue();
            AppearanceEntity scriptEntity = script.getValue();
            AppearanceEntity globalEntity = global.getValue();
            result.setValue(resolve(characterEntity, scriptEntity, globalEntity));
        };

        result.addSource(script, v -> recompute.run());
        result.addSource(global, v -> recompute.run());
        if (character != null) {
            result.addSource(character, v -> recompute.run());
        }
        return result;
    }

    @Override
    public Appearance getEffective(String scriptId, @Nullable String characterId) {
        AppearanceEntity character = characterId == null ? null : dao.getByScope(Appearance.ScopeType.CHARACTER.name(), characterId);
        AppearanceEntity script = dao.getByScope(Appearance.ScopeType.SCRIPT.name(), scriptId);
        AppearanceEntity global = dao.getByScope(Appearance.ScopeType.GLOBAL.name(), "global");
        return resolve(character, script, global);
    }

    @Override
    public Appearance getByScope(Appearance.ScopeType scopeType, String scopeId) {
        AppearanceEntity entity = dao.getByScope(scopeType.name(), scopeId);
        return entity == null ? null : EntityMapper.toAppearance(entity);
    }

    @Override
    public void save(Appearance appearance) {
        db.runInTransaction(() -> dao.insert(EntityMapper.toEntity(appearance)));
    }

    @Override
    public Appearance getOrCreate(Appearance.ScopeType scopeType, String scopeId) {
        AppearanceEntity entity = dao.getByScope(scopeType.name(), scopeId);
        if (entity == null) {
            Appearance fallback = getEffectiveByScopeFallback(scopeType, scopeId);
            Appearance created = new Appearance(
                    java.util.UUID.randomUUID().toString(),
                    scopeType,
                    scopeId,
                    fallback.getBackgroundType(),
                    fallback.getBackgroundRef(),
                    fallback.getBackgroundMode(),
                    fallback.getBackgroundDimAlpha(),
                    fallback.getBubbleStyleId(),
                    fallback.getBubbleColor(),
                    fallback.getTextColor(),
                    fallback.getNicknameColor(),
                    fallback.getFontScale());
            db.runInTransaction(() -> dao.insert(EntityMapper.toEntity(created)));
            return created;
        }
        return EntityMapper.toAppearance(entity);
    }

    private Appearance getEffectiveByScopeFallback(Appearance.ScopeType scopeType, String scopeId) {
        if (scopeType == Appearance.ScopeType.GLOBAL) {
            return defaultAppearance();
        }
        AppearanceEntity global = dao.getByScope(Appearance.ScopeType.GLOBAL.name(), "global");
        return resolve(null, null, global);
    }

    private Appearance resolve(@Nullable AppearanceEntity character,
                               @Nullable AppearanceEntity script,
                               @Nullable AppearanceEntity global) {
        Appearance base = global == null ? defaultAppearance() : EntityMapper.toAppearance(global);
        if (script != null) {
            base = overlay(base, EntityMapper.toAppearance(script));
        }
        if (character != null) {
            base = overlay(base, EntityMapper.toAppearance(character));
        }
        return base;
    }

    /** 叠加：非 null 字段覆盖下层。 */
    private Appearance overlay(Appearance base, Appearance override) {
        return new Appearance(
                override.getId(),
                override.getScopeType(),
                override.getScopeId(),
                override.getBackgroundType() != null ? override.getBackgroundType() : base.getBackgroundType(),
                override.getBackgroundRef() != null ? override.getBackgroundRef() : base.getBackgroundRef(),
                override.getBackgroundMode() != null ? override.getBackgroundMode() : base.getBackgroundMode(),
                override.getBackgroundDimAlpha() != 0 ? override.getBackgroundDimAlpha() : base.getBackgroundDimAlpha(),
                override.getBubbleStyleId() != null ? override.getBubbleStyleId() : base.getBubbleStyleId(),
                override.getBubbleColor() != null ? override.getBubbleColor() : base.getBubbleColor(),
                override.getTextColor() != null ? override.getTextColor() : base.getTextColor(),
                override.getNicknameColor() != null ? override.getNicknameColor() : base.getNicknameColor(),
                override.getFontScale() != 0 ? override.getFontScale() : base.getFontScale());
    }

    private Appearance defaultAppearance() {
        // 默认值 = S9 设计色板（浅灰绿/米白/暖棕），用户自定义时覆盖
        return new Appearance(
                "default-global",
                Appearance.ScopeType.GLOBAL,
                "global",
                Appearance.BackgroundType.BUILTIN,
                null,
                Appearance.BackgroundMode.CENTER_CROP,
                0f,
                "rounded_v1",
                "#FFB8E6C1",
                "#FF24352A",
                "#FF846044",
                1.0f);
    }
}
