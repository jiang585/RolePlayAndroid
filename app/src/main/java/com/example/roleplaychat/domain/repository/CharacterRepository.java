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

    void deleteCharacter(String characterId);

    /** 剧本内下一个排序值。 */
    int nextSortIndex(String scriptId);

    List<CharacterProfile> getByIds(List<String> characterIds);
}
