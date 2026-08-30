package com.example.roleplaychat.ui.world;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.roleplaychat.domain.model.WorldSetting;
import com.example.roleplaychat.domain.repository.ScriptRepository;
import com.example.roleplaychat.domain.repository.WorldRepository;
import com.example.roleplaychat.ui.common.SingleEvent;
import com.example.roleplaychat.util.AppExecutors;

/**
 * 对话设定 ViewModel：剧本级扮演要求 + 每轮最多回复角色数。
 * 编辑落到 world_settings 行（与世界观 1:1，剧本首次设置时创建行）。
 */
public class ChatRuleEditViewModel extends ViewModel {

    private final WorldRepository worldRepository;
    private final ScriptRepository scriptRepository;
    private final AppExecutors executors;

    private final MutableLiveData<SingleEvent<String>> events = new MutableLiveData<>();
    private final MutableLiveData<WorldSetting> world = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loaded = new MutableLiveData<>(false);
    private String scriptId;

    public ChatRuleEditViewModel(WorldRepository worldRepository,
                                 ScriptRepository scriptRepository,
                                 AppExecutors executors) {
        this.worldRepository = worldRepository;
        this.scriptRepository = scriptRepository;
        this.executors = executors;
    }

    /** 进入页面时加载当前规则（DB 操作在后台线程）。 */
    public void start(String scriptId) {
        if (this.scriptId != null) {
            return;
        }
        this.scriptId = scriptId;
        executors.diskIO().execute(() -> {
            world.postValue(worldRepository.getByScriptId(scriptId));
            loaded.postValue(true);
        });
    }

    public LiveData<WorldSetting> getWorld() {
        return world;
    }

    public LiveData<Boolean> getLoaded() {
        return loaded;
    }

    public LiveData<SingleEvent<String>> getEvents() {
        return events;
    }

    public void save(String styleDirective, int maxResponders, long now) {
        if (scriptId == null) {
            events.postValue(new SingleEvent<>("error:no_script"));
            return;
        }
        executors.diskIO().execute(() -> {
            // 保留世界观其余字段：对话规则只是 world_settings 行的一部分。
            WorldSetting base = worldRepository.getByScriptId(scriptId);
            WorldSetting updated = new WorldSetting(
                    base == null ? java.util.UUID.randomUUID().toString() : base.getId(),
                    scriptId,
                    base == null ? null : base.getEra(),
                    base == null ? null : base.getLocation(),
                    base == null ? null : base.getFactions(),
                    base == null ? null : base.getRules(),
                    base == null ? null : base.getStoryHook(),
                    base == null ? null : base.getBackgroundFull(),
                    base == null ? null : base.getTags(),
                    base == null ? null : base.getVersionNote(),
                    styleDirective,
                    maxResponders,
                    now);
            worldRepository.save(updated);
            scriptRepository.touchUpdatedAt(scriptId, now);
            events.postValue(new SingleEvent<>("saved"));
        });
    }
}
