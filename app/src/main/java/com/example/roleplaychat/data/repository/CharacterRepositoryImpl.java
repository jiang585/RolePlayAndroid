package com.example.roleplaychat.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.example.roleplaychat.data.local.AppDatabase;
import com.example.roleplaychat.data.local.dao.CharacterDao;
import com.example.roleplaychat.data.local.entity.CharacterEntity;
import com.example.roleplaychat.data.mapper.EntityMapper;
import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.repository.CharacterRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色仓库实现。
 */
public class CharacterRepositoryImpl implements CharacterRepository {

    private final AppDatabase db;
    private final CharacterDao dao;

    public CharacterRepositoryImpl(AppDatabase db) {
        this.db = db;
        this.dao = db.characterDao();
    }

    @Override
    public LiveData<List<CharacterProfile>> observeByScriptId(String scriptId) {
        return Transformations.map(dao.observeByScriptId(scriptId), entities ->
                entities.stream().map(EntityMapper::toProfile).collect(Collectors.toList()));
    }

    @Override
    public List<CharacterProfile> getEnabledByScriptId(String scriptId) {
        return dao.getEnabledByScriptId(scriptId).stream()
                .map(EntityMapper::toProfile)
                .collect(Collectors.toList());
    }

    @Override
    public CharacterProfile getById(String characterId) {
        CharacterEntity entity = dao.getById(characterId);
        return entity == null ? null : EntityMapper.toProfile(entity);
    }

    @Override
    public void save(CharacterProfile profile) {
        db.runInTransaction(() -> {
            CharacterEntity existing = dao.getById(profile.getId());
            if (existing != null) {
                dao.update(EntityMapper.toEntity(profile));
            } else {
                dao.insert(EntityMapper.toEntity(profile));
            }
        });
    }

    @Override
    public void setEnabled(String characterId, boolean enabled, long now) {
        db.runInTransaction(() -> {
            CharacterEntity entity = dao.getById(characterId);
            if (entity == null) {
                return;
            }
            dao.setEnabled(characterId, enabled, now);
            if (!enabled) {
                com.example.roleplaychat.data.local.entity.SessionMemberEntity member =
                        db.sessionMemberDao().getMemberByCharacter(entity.script_id, characterId);
                db.sessionMemberDao().deactivateByCharacter(entity.script_id, characterId);
                if (member != null && member.active
                        && com.example.roleplaychat.data.local.entity.SessionMemberEntity.MEMBER_PLAYER
                        .equals(member.member_type)) {
                    db.sessionMemberDao().activateObserverSlot(entity.script_id);
                }
            } else {
                db.sessionMemberDao().activateNpc(entity.script_id, characterId);
            }
        });
    }

    @Override
    public void disableCharacter(String characterId) {
        // 软停用：保留角色卡与历史消息，只停止参与后续编排。
        setEnabled(characterId, false, System.currentTimeMillis());
    }

    @Override
    public void deleteCharacter(String characterId) {
        // 硬删除：session_members 外键为 RESTRICT，必须先清理成员行；
        // 消息外键为 SET_NULL，历史靠 sender_*_snapshot 字段继续显示。
        db.runInTransaction(() -> {
            CharacterEntity entity = dao.getById(characterId);
            if (entity == null) {
                return;
            }
            com.example.roleplaychat.data.local.entity.SessionMemberEntity member =
                    db.sessionMemberDao().getMemberByCharacter(entity.script_id, characterId);
            boolean wasActivePlayer = member != null && member.active
                    && com.example.roleplaychat.data.local.entity.SessionMemberEntity.MEMBER_PLAYER
                    .equals(member.member_type);
            db.sessionMemberDao().deleteByCharacter(entity.script_id, characterId);
            if (wasActivePlayer) {
                // 玩家身份随角色一起消失，回退到观察者席，避免聊天页出现空身份。
                db.sessionMemberDao().activateObserverSlot(entity.script_id);
            }
            dao.deleteById(characterId);
        });
    }

    @Override
    public int nextSortIndex(String scriptId) {
        return dao.nextSortIndex(scriptId);
    }

    @Override
    public List<CharacterProfile> getByIds(List<String> characterIds) {
        if (characterIds == null || characterIds.isEmpty()) {
            return new ArrayList<>();
        }
        return dao.getByIds(characterIds).stream()
                .map(EntityMapper::toProfile)
                .collect(Collectors.toList());
    }
}
