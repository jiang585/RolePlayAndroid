package com.example.roleplaychat.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;

import com.example.roleplaychat.data.local.entity.SessionMemberEntity;

import java.util.List;

/**
 * 群成员 DAO（架构文档 §6.2 session_members）。
 */
@Dao
public interface SessionMemberDao {

    @Query("SELECT * FROM session_members WHERE script_id = :scriptId AND member_type = 'PLAYER' AND active = 1 LIMIT 1")
    SessionMemberEntity getActivePlayer(String scriptId);

    @Query("SELECT * FROM session_members WHERE script_id = :scriptId AND member_type = 'PLAYER' AND active = 1 LIMIT 1")
    LiveData<SessionMemberEntity> observeActivePlayer(String scriptId);

    @Query("UPDATE session_members SET active = 0 WHERE script_id = :scriptId AND member_type = 'PLAYER'")
    int deactivateAllPlayers(String scriptId);

    @Query("UPDATE session_members SET active = 1, member_type = 'PLAYER', " +
            "player_role_type = :roleType, character_id = :characterId WHERE id = :memberId")
    int updatePlayerMember(String memberId, String roleType, String characterId);

    @Query("UPDATE session_members SET active = 1, member_type = 'NPC', player_role_type = NULL " +
            "WHERE id = :memberId AND character_id IS NOT NULL")
    int restoreNpcMember(String memberId);

    @Query("SELECT * FROM session_members WHERE script_id = :scriptId AND character_id = :characterId LIMIT 1")
    SessionMemberEntity getMemberByCharacter(String scriptId, String characterId);

    @Query("SELECT * FROM session_members WHERE script_id = :scriptId AND member_type = 'PLAYER' " +
            "AND character_id IS NULL LIMIT 1")
    SessionMemberEntity getObserverSlot(String scriptId);

    @Query("SELECT * FROM session_members WHERE script_id = :scriptId AND active = 1")
    List<SessionMemberEntity> getActiveMembers(String scriptId);

    @Query("INSERT OR REPLACE INTO session_members (id, script_id, character_id, member_type, player_role_type, active, joined_at) " +
            "VALUES (:id, :scriptId, :characterId, :memberType, :playerRoleType, :active, :joinedAt)")
    void upsertMember(String id, String scriptId, String characterId, String memberType,
                      String playerRoleType, boolean active, long joinedAt);

    @Query("UPDATE session_members SET active = 0 WHERE script_id = :scriptId AND character_id = :characterId")
    int deactivateByCharacter(String scriptId, String characterId);

    @Query("UPDATE session_members SET active = 1, member_type = 'NPC' WHERE script_id = :scriptId AND character_id = :characterId")
    int activateNpc(String scriptId, String characterId);

    @Query("UPDATE session_members SET active = 1, player_role_type = 'OBSERVER' " +
            "WHERE script_id = :scriptId AND member_type = 'PLAYER' AND character_id IS NULL")
    int activateObserverSlot(String scriptId);
}
