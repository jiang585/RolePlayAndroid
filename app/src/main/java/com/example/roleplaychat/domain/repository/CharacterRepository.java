package com.example.roleplaychat.domain.repository;

import androidx.lifecycle.LiveData;

import com.example.roleplaychat.domain.model.CharacterProfile;

import java.util.List;

/**
 * 角色仓库接口。
 */
public interface CharacterRepository {

    LiveData<List<CharacterProfile>> observeByScriptId(String scriptId);

    List<CharacterProfile> getEnabledByScriptId(String scriptId);

    CharacterProfile getById(String characterId);

    /** 插入或更新角色。 */
    void save(CharacterProfile profile);

    /** 停用角色（不删除历史）。 */
    void setEnabled(String characterId, boolean enabled, long now);

    /** 停用角色：保留角色卡与历史消息，仅停止参与后续编排。 */
    void disableCharacter(String characterId);

    /** 永久删除角色：历史消息保留但不再关联该角色（character_id 置空，靠快照字段显示）。 */
    void deleteCharacter(String characterId);

    /** 剧本内下一个排序值。 */
    int nextSortIndex(String scriptId);

    List<CharacterProfile> getByIds(List<String> characterIds);
}
