package com.example.roleplaychat.domain.repository;

import androidx.lifecycle.LiveData;

import com.example.roleplaychat.domain.model.AiRequest;
import com.example.roleplaychat.domain.model.PlayerIdentity;
import com.example.roleplaychat.domain.model.Script;

import java.util.List;

/**
 * 剧本仓库接口（架构文档 §7.1 示例风格）。
 */
public interface ScriptRepository {

    LiveData<List<Script>> observeAll();

    List<Script> getAll();

    LiveData<Script> observeById(String scriptId);

    Script getById(String scriptId);

    /** 创建剧本：同事务创建 Script、World、SCRIPT Appearance 与 Player slot（§7.2）。 */
    String createScript(String name, String oneLine, long now);

    void updateScript(Script script);

    void touchUpdatedAt(String scriptId, long now);

    /** 删除剧本：事务级联删除子记录。 */
    void deleteScript(String scriptId);

    LiveData<PlayerIdentity> observePlayerIdentity(String scriptId);

    PlayerIdentity getPlayerIdentity(String scriptId);

    void setPlayerIdentity(PlayerIdentity identity);
}
