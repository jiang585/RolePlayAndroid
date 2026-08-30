package com.example.roleplaychat.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.example.roleplaychat.data.local.AppDatabase;
import com.example.roleplaychat.data.local.dao.ScriptDao;
import com.example.roleplaychat.data.local.dao.SessionMemberDao;
import com.example.roleplaychat.data.local.entity.AppearanceEntity;
import com.example.roleplaychat.data.local.entity.ScriptEntity;
import com.example.roleplaychat.data.local.entity.SessionMemberEntity;
import com.example.roleplaychat.data.local.entity.WorldSettingEntity;
import com.example.roleplaychat.data.mapper.EntityMapper;
import com.example.roleplaychat.domain.model.Appearance;
import com.example.roleplaychat.domain.model.PlayerIdentity;
import com.example.roleplaychat.domain.model.Script;
import com.example.roleplaychat.domain.repository.ScriptRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 剧本仓库实现（架构文档 §7.2：创建剧本同事务建 Script/World/Appearance/Player slot）。
 */
public class ScriptRepositoryImpl implements ScriptRepository {

    private final AppDatabase db;
    private final ScriptDao dao;

    public ScriptRepositoryImpl(AppDatabase db) {
        this.db = db;
        this.dao = db.scriptDao();
    }

    @Override
    public LiveData<List<Script>> observeAll() {
        return Transformations.map(dao.observeAll(), entities ->
                entities.stream().map(EntityMapper::toScript).collect(Collectors.toList()));
    }

    @Override
    public List<Script> getAll() {
        return dao.getAll().stream().map(EntityMapper::toScript).collect(Collectors.toList());
    }

    @Override
    public LiveData<Script> observeById(String scriptId) {
        return Transformations.map(dao.observeById(scriptId), EntityMapper::toScript);
    }

    @Override
    public Script getById(String scriptId) {
        ScriptEntity entity = dao.getById(scriptId);
        return entity == null ? null : EntityMapper.toScript(entity);
    }

    @Override
    public String createScript(String name, String oneLine, long now) {
        String scriptId = java.util.UUID.randomUUID().toString();
        ScriptEntity script = new ScriptEntity(scriptId, name, oneLine, null, now, now, 0);
        WorldSettingEntity world = new WorldSettingEntity(
                java.util.UUID.randomUUID().toString(), scriptId, null, null,
                "[]", "[]", null, null, "[]", null, now);
        AppearanceEntity scriptAppearance = defaultAppearance(Appearance.ScopeType.SCRIPT, scriptId);
        AppearanceEntity globalAppearance = defaultAppearance(Appearance.ScopeType.GLOBAL, "global");
        SessionMemberEntity playerSlot = new SessionMemberEntity(
                java.util.UUID.randomUUID().toString(), scriptId, null,
                SessionMemberEntity.MEMBER_PLAYER, PlayerIdentity.RoleType.OBSERVER.name(), true, now);
        // 事务内插入；全局装扮若不存在则一并建立
        db.runInTransaction(() -> {
            dao.insert(script);
            dao.worldInsert(world);
            dao.appearanceInsert(scriptAppearance);
            if (db.appearanceDao().getByScope("GLOBAL", "global") == null) {
                dao.appearanceInsert(globalAppearance);
            }
            dao.memberInsert(playerSlot);
        });
        return scriptId;
    }

    @Override
    public void updateScript(Script script) {
        db.runInTransaction(() -> dao.update(EntityMapper.toEntity(script)));
    }

    @Override
    public void touchUpdatedAt(String scriptId, long now) {
        db.runInTransaction(() -> dao.touchUpdatedAt(scriptId, now));
    }

    @Override
    public void deleteScript(String scriptId) {
        db.runInTransaction(() -> dao.deleteById(scriptId));
    }

    @Override
    public LiveData<PlayerIdentity> observePlayerIdentity(String scriptId) {
        return Transformations.map(db.sessionMemberDao().observeActivePlayer(scriptId), EntityMapper::toIdentity);
    }

    @Override
    public PlayerIdentity getPlayerIdentity(String scriptId) {
        SessionMemberEntity member = db.sessionMemberDao().getActivePlayer(scriptId);
        PlayerIdentity identity = EntityMapper.toIdentity(member);
        if (identity != null && identity.getCharacterId() != null
                && db.characterDao().getById(identity.getCharacterId()) == null) {
            // 防御：绑定的角色已被硬删除时回退观察者，避免空身份阻塞后续发言。
            return new PlayerIdentity(scriptId, PlayerIdentity.RoleType.OBSERVER, null,
                    identity.getChangedAt());
        }
        return identity;
    }

    @Override
    public void setPlayerIdentity(PlayerIdentity identity) {
        db.runInTransaction(() -> {
            SessionMemberDao memberDao = db.sessionMemberDao();
            SessionMemberEntity current = memberDao.getActivePlayer(identity.getScriptId());
            memberDao.deactivateAllPlayers(identity.getScriptId());
            if (current != null && current.character_id != null) {
                // 被玩家放下的角色继续作为群聊 NPC 参与会话。
                memberDao.restoreNpcMember(current.id);
            }

            SessionMemberEntity existing = identity.getCharacterId() == null
                    ? memberDao.getObserverSlot(identity.getScriptId())
                    : memberDao.getMemberByCharacter(identity.getScriptId(), identity.getCharacterId());
            if (existing != null) {
                memberDao.updatePlayerMember(existing.id, identity.getRoleType().name(), identity.getCharacterId());
            } else {
                memberDao.upsertMember(
                        java.util.UUID.randomUUID().toString(),
                        identity.getScriptId(),
                        identity.getCharacterId(),
                        SessionMemberEntity.MEMBER_PLAYER,
                        identity.getRoleType().name(),
                        true,
                        identity.getChangedAt());
            }
        });
    }

    private AppearanceEntity defaultAppearance(Appearance.ScopeType scopeType, String scopeId) {
        // 默认值 = S9 设计色板
        return new AppearanceEntity(
                java.util.UUID.randomUUID().toString(),
                scopeType.name(),
                scopeId,
                Appearance.BackgroundType.BUILTIN.name(),
                null,
                Appearance.BackgroundMode.CENTER_CROP.name(),
                0f,
                "rounded_v1",
                "#FFB8E6C1",
                "#FF24352A",
                "#FF846044",
                1.0f);
    }
}
