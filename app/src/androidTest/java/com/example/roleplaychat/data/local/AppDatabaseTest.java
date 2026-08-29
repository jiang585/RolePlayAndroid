package com.example.roleplaychat.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.roleplaychat.data.local.entity.CharacterEntity;
import com.example.roleplaychat.data.local.entity.MessageEntity;
import com.example.roleplaychat.data.local.entity.ScriptEntity;
import com.example.roleplaychat.data.local.entity.SessionMemberEntity;
import com.example.roleplaychat.data.local.entity.WorldSettingEntity;
import com.example.roleplaychat.data.repository.ScriptRepositoryImpl;
import com.example.roleplaychat.data.repository.CharacterRepositoryImpl;
import com.example.roleplaychat.data.repository.WorldRepositoryImpl;
import com.example.roleplaychat.domain.model.PlayerIdentity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据库测试（架构文档 §15.1 数据集成层）：
 * 外键级联删除、sequence 分配、消息分页。
 */
@RunWith(AndroidJUnit4.class)
public class AppDatabaseTest {

    private AppDatabase db;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void deleteScript_cascadesToChildren() {
        long now = System.currentTimeMillis();
        String scriptId = "script-1";
        ScriptEntity script = new ScriptEntity(scriptId, "测试剧本", null, null, now, now, 0);
        db.scriptDao().insert(script);
        db.characterDao().insert(new CharacterEntity("char-1", scriptId, "张三", "[]",
                null, null, null, null, null, null,
                "[]", "[]", "[]", "{}", "[]", null, null, true, 0, now, now, null));
        db.messageDao().insert(createMessage("msg-1", scriptId, 1, now));

        db.scriptDao().deleteById(scriptId);

        assertNull(db.characterDao().getById("char-1"));
        assertEquals(0, db.messageDao().countByScript(scriptId));
    }

    @Test
    public void sequence_assignedMonotonicallyInTransaction() {
        long now = System.currentTimeMillis();
        String scriptId = "script-1";
        db.scriptDao().insert(new ScriptEntity(scriptId, "剧本", null, null, now, now, 0));

        MessageEntity m1 = createMessage("m1", scriptId, 0, now);
        MessageEntity m2 = createMessage("m2", scriptId, 0, now);
        long s1 = db.messageDao().insertPlayerMessageTx(m1);
        long s2 = db.messageDao().insertPlayerMessageTx(m2);

        assertEquals(1L, s1);
        assertEquals(2L, s2);

        // 批量插入连续分配
        List<MessageEntity> batch = new ArrayList<>();
        batch.add(createMessage("b1", scriptId, 0, now));
        batch.add(createMessage("b2", scriptId, 0, now));
        db.messageDao().insertAiBatchTx(batch);
        assertEquals(3L, batch.get(0).sequence);
        assertEquals(4L, batch.get(1).sequence);
    }

    @Test
    public void loadBefore_paginatesInSequenceOrder() {
        long now = System.currentTimeMillis();
        String scriptId = "script-1";
        db.scriptDao().insert(new ScriptEntity(scriptId, "剧本", null, null, now, now, 0));
        for (int i = 1; i <= 10; i++) {
            db.messageDao().insert(createMessage("m" + i, scriptId, i, now));
        }
        List<MessageEntity> before = db.messageDao().loadBefore(scriptId, 7, 3);
        // sequence < 7 的最多 3 条：6,5,4
        assertEquals(3, before.size());
        assertEquals(6L, before.get(0).sequence);
        assertEquals(5L, before.get(1).sequence);
        assertEquals(4L, before.get(2).sequence);
    }

    @Test
    public void switchIdentity_promotesTargetAndRestoresPreviousCharacterAsNpc() {
        long now = System.currentTimeMillis();
        String scriptId = "script-identity";
        db.scriptDao().insert(new ScriptEntity(scriptId, "Identity", null, null, now, now, 0));
        db.characterDao().insert(createCharacter("char-a", scriptId, now));
        db.characterDao().insert(createCharacter("char-b", scriptId, now));
        db.sessionMemberDao().upsertMember("observer", scriptId, null,
                SessionMemberEntity.MEMBER_PLAYER, PlayerIdentity.RoleType.OBSERVER.name(), false, now);
        db.sessionMemberDao().upsertMember("member-a", scriptId, "char-a",
                SessionMemberEntity.MEMBER_PLAYER, PlayerIdentity.RoleType.PROTAGONIST.name(), true, now);
        db.sessionMemberDao().upsertMember("member-b", scriptId, "char-b",
                SessionMemberEntity.MEMBER_NPC, null, true, now);

        ScriptRepositoryImpl repository = new ScriptRepositoryImpl(db);
        repository.setPlayerIdentity(new PlayerIdentity(scriptId,
                PlayerIdentity.RoleType.SUPPORTING, "char-b", now + 1));

        SessionMemberEntity previous = db.sessionMemberDao().getMemberByCharacter(scriptId, "char-a");
        SessionMemberEntity active = db.sessionMemberDao().getActivePlayer(scriptId);
        assertEquals(SessionMemberEntity.MEMBER_NPC, previous.member_type);
        assertEquals(true, previous.active);
        assertNull(previous.player_role_type);
        assertEquals("char-b", active.character_id);
        assertEquals(SessionMemberEntity.MEMBER_PLAYER, active.member_type);

        repository.setPlayerIdentity(new PlayerIdentity(scriptId,
                PlayerIdentity.RoleType.OBSERVER, null, now + 2));
        assertNull(db.sessionMemberDao().getActivePlayer(scriptId).character_id);
        SessionMemberEntity formerPlayer = db.sessionMemberDao().getMemberByCharacter(scriptId, "char-b");
        assertEquals(SessionMemberEntity.MEMBER_NPC, formerPlayer.member_type);
        assertEquals(true, formerPlayer.active);
    }

    @Test
    public void deleteCurrentCharacter_disablesItAndFallsBackToObserver() {
        long now = System.currentTimeMillis();
        String scriptId = "script-delete-character";
        db.scriptDao().insert(new ScriptEntity(scriptId, "Delete", null, null, now, now, 0));
        db.characterDao().insert(createCharacter("char-player", scriptId, now));
        db.sessionMemberDao().upsertMember("observer", scriptId, null,
                SessionMemberEntity.MEMBER_PLAYER, PlayerIdentity.RoleType.OBSERVER.name(), false, now);
        db.sessionMemberDao().upsertMember("player", scriptId, "char-player",
                SessionMemberEntity.MEMBER_PLAYER, PlayerIdentity.RoleType.PROTAGONIST.name(), true, now);

        new CharacterRepositoryImpl(db).deleteCharacter("char-player");

        assertNotNull(db.characterDao().getById("char-player"));
        assertEquals(false, db.characterDao().getById("char-player").enabled);
        assertNull(db.sessionMemberDao().getActivePlayer(scriptId).character_id);
    }

    @Test
    public void world_upsertByScriptId() {
        long now = System.currentTimeMillis();
        String scriptId = "script-1";
        db.scriptDao().insert(new ScriptEntity(scriptId, "剧本", null, null, now, now, 0));
        WorldRepositoryImpl repository = new WorldRepositoryImpl(db);
        repository.save(new com.example.roleplaychat.domain.model.WorldSetting(
                "world-1", scriptId, "现代", "城市",
                java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                "主线", "背景", java.util.Collections.emptyList(), null, now));
        WorldSettingEntity world = db.worldSettingDao().getByScriptId(scriptId);
        assertNotNull(world);
        assertEquals("现代", world.era);

        repository.save(new com.example.roleplaychat.domain.model.WorldSetting(
                "world-1", scriptId, "未来", "空间站",
                java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                "新主线", "新背景", java.util.Collections.emptyList(), null, now + 1));
        assertEquals("未来", db.worldSettingDao().getByScriptId(scriptId).era);
    }

    private MessageEntity createMessage(String id, String scriptId, long sequence, long now) {
        return new MessageEntity(id, scriptId, null, null, null, null, null,
                "CHARACTER_TEXT", "MINE", "内容", sequence, now,
                "DONE", null, null, null, null, null);
    }

    private CharacterEntity createCharacter(String id, String scriptId, long now) {
        return new CharacterEntity(id, scriptId, id, "[]", null, null, null,
                null, null, null, "[]", "[]", "[]", "{}", "[]",
                null, null, true, 0, now, now, null);
    }
}
