package com.example.roleplaychat.ui.appearance;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.roleplaychat.data.file.ImageImporter;
import com.example.roleplaychat.data.file.LocalAssetStore;
import com.example.roleplaychat.domain.model.Appearance;
import com.example.roleplaychat.domain.repository.AppearanceRepository;
import com.example.roleplaychat.domain.repository.CharacterRepository;
import com.example.roleplaychat.ui.common.SingleEvent;

/**
 * 装扮设置 ViewModel（FR-601~606）。
 */
public class AppearanceViewModel extends ViewModel {

    private final AppearanceRepository appearanceRepository;
    private final CharacterRepository characterRepository;
    private final ImageImporter imageImporter;
    private final com.example.roleplaychat.util.AppExecutors executors;

    private final MutableLiveData<SingleEvent<String>> events = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loaded = new MutableLiveData<>(false);
    private Appearance editing;
    private Appearance.ScopeType scopeType;
    private String scopeId;

    public AppearanceViewModel(AppearanceRepository appearanceRepository,
                               CharacterRepository characterRepository,
                               ImageImporter imageImporter,
                               com.example.roleplaychat.util.AppExecutors executors) {
        this.appearanceRepository = appearanceRepository;
        this.characterRepository = characterRepository;
        this.imageImporter = imageImporter;
        this.executors = executors;
    }

    public MutableLiveData<SingleEvent<String>> getEvents() {
        return events;
    }

    public LiveData<Boolean> getLoaded() {
        return loaded;
    }

    /** 编辑剧本装扮。 */
    public void loadScriptAppearance(String scriptId) {
        scopeType = Appearance.ScopeType.SCRIPT;
        scopeId = scriptId;
        executors.diskIO().execute(() -> {
            editing = appearanceRepository.getOrCreate(Appearance.ScopeType.SCRIPT, scriptId);
            loaded.postValue(true);
        });
    }

    /** 编辑全局装扮。 */
    public void loadGlobalAppearance() {
        scopeType = Appearance.ScopeType.GLOBAL;
        scopeId = "global";
        executors.diskIO().execute(() -> {
            editing = appearanceRepository.getOrCreate(Appearance.ScopeType.GLOBAL, "global");
            loaded.postValue(true);
        });
    }

    /** 编辑角色专属装扮。 */
    public void loadCharacterAppearance(String characterId) {
        scopeType = Appearance.ScopeType.CHARACTER;
        scopeId = characterId;
        executors.diskIO().execute(() -> {
            editing = appearanceRepository.getOrCreate(Appearance.ScopeType.CHARACTER, characterId);
            loaded.postValue(true);
        });
    }

    public Appearance getEditing() {
        return editing;
    }

    public void chooseBackgroundImage(android.net.Uri uri) {
        if (uri == null || editing == null) {
            return;
        }
        executors.diskIO().execute(() -> {
            String ref = imageImporter.importImage(LocalAssetStore.DIR_BACKGROUNDS, uri);
            if (ref != null) {
                editing = editing.copyWith(Appearance.BackgroundType.IMAGE, ref,
                        Appearance.BackgroundMode.CENTER_CROP, editing.getBackgroundDimAlpha(),
                        editing.getBubbleStyleId(), editing.getBubbleColor(), editing.getTextColor(),
                        editing.getNicknameColor(), editing.getFontScale());
                events.postValue(new SingleEvent<>("changed"));
            } else {
                events.postValue(new SingleEvent<>("error:image"));
            }
        });
    }

    public void resetBackground() {
        if (editing == null) {
            return;
        }
        editing = editing.copyWith(Appearance.BackgroundType.BUILTIN, null,
                Appearance.BackgroundMode.CENTER_CROP, 0f,
                editing.getBubbleStyleId(), editing.getBubbleColor(), editing.getTextColor(),
                editing.getNicknameColor(), editing.getFontScale());
        events.setValue(new SingleEvent<>("changed"));
    }

    public void setBackgroundDim(float alpha) {
        if (editing == null) {
            return;
        }
        editing = editing.copyWith(editing.getBackgroundType(), editing.getBackgroundRef(),
                editing.getBackgroundMode(), alpha,
                editing.getBubbleStyleId(), editing.getBubbleColor(), editing.getTextColor(),
                editing.getNicknameColor(), editing.getFontScale());
        events.setValue(new SingleEvent<>("changed"));
    }

    public void setBubbleColor(String color) {
        if (editing == null) {
            return;
        }
        editing = editing.copyWith(editing.getBackgroundType(), editing.getBackgroundRef(),
                editing.getBackgroundMode(), editing.getBackgroundDimAlpha(),
                editing.getBubbleStyleId(), color, editing.getTextColor(),
                editing.getNicknameColor(), editing.getFontScale());
        events.setValue(new SingleEvent<>("changed"));
    }

    public void setTextColor(String color) {
        if (editing == null) {
            return;
        }
        editing = editing.copyWith(editing.getBackgroundType(), editing.getBackgroundRef(),
                editing.getBackgroundMode(), editing.getBackgroundDimAlpha(),
                editing.getBubbleStyleId(), editing.getBubbleColor(), color,
                editing.getNicknameColor(), editing.getFontScale());
        events.setValue(new SingleEvent<>("changed"));
    }

    public void setNicknameColor(String color) {
        if (editing == null) {
            return;
        }
        editing = editing.copyWith(editing.getBackgroundType(), editing.getBackgroundRef(),
                editing.getBackgroundMode(), editing.getBackgroundDimAlpha(),
                editing.getBubbleStyleId(), editing.getBubbleColor(), editing.getTextColor(),
                color, editing.getFontScale());
        events.setValue(new SingleEvent<>("changed"));
    }

    public void setBubbleStyle(String styleId) {
        if (editing == null) {
            return;
        }
        editing = editing.copyWith(editing.getBackgroundType(), editing.getBackgroundRef(),
                editing.getBackgroundMode(), editing.getBackgroundDimAlpha(),
                styleId, editing.getBubbleColor(), editing.getTextColor(),
                editing.getNicknameColor(), editing.getFontScale());
        events.setValue(new SingleEvent<>("changed"));
    }

    public void save() {
        if (editing == null) {
            events.postValue(new SingleEvent<>("error:not_loaded"));
            return;
        }
        executors.diskIO().execute(() -> {
            appearanceRepository.save(editing);
            events.postValue(new SingleEvent<>("saved"));
        });
    }
}
