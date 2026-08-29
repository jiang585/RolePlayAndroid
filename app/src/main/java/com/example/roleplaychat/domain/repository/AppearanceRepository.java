package com.example.roleplaychat.domain.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.example.roleplaychat.domain.model.Appearance;

/**
 * 装扮仓库接口。解析优先级：角色覆盖 > 剧本设置 > 全局设置 > 应用默认。
 */
public interface AppearanceRepository {

    LiveData<Appearance> observeEffective(String scriptId, @Nullable String characterId);

    /** 解析指定作用域链上的有效装扮。 */
    Appearance getEffective(String scriptId, @Nullable String characterId);

    Appearance getByScope(Appearance.ScopeType scopeType, String scopeId);

    void save(Appearance appearance);

    /** 获取（或创建）指定作用域的装扮记录，用于编辑。 */
    Appearance getOrCreate(Appearance.ScopeType scopeType, String scopeId);
}
