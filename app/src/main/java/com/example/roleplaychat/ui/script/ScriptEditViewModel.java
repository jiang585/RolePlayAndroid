package com.example.roleplaychat.ui.script;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.example.roleplaychat.data.file.ImageImporter;
import com.example.roleplaychat.data.file.LocalAssetStore;
import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.Script;
import com.example.roleplaychat.domain.repository.ScriptRepository;
import com.example.roleplaychat.domain.usecase.CreateScriptUseCase;
import com.example.roleplaychat.ui.common.SingleEvent;
import com.example.roleplaychat.util.AppExecutors;

import android.net.Uri;

/**
 * 剧本编辑 ViewModel（新建或编辑）。DB 操作在后台线程（§3.2）。
 * 支持封面上传 / 清除；封面文件存 covers 目录，DB 仅存相对引用。
 */
public class ScriptEditViewModel extends ViewModel {

    private final ScriptRepository scriptRepository;
    private final CreateScriptUseCase createScriptUseCase;
    private final ImageImporter imageImporter;
    private final AppExecutors executors;
    private final MutableLiveData<SingleEvent<String>> events = new MutableLiveData<>();
    private final MutableLiveData<Script> script = new MutableLiveData<>();
    private final MutableLiveData<String> coverRef = new MutableLiveData<>();
    private final Observer<Script> scriptObserver = value -> {
        script.postValue(value);
        initCoverOnce(value == null ? null : value.getCoverRef());
    };

    private LiveData<Script> scriptSource;
    private boolean coverInitialized;

    public ScriptEditViewModel(ScriptRepository scriptRepository,
                               CreateScriptUseCase createScriptUseCase,
                               ImageImporter imageImporter,
                               AppExecutors executors) {
        this.scriptRepository = scriptRepository;
        this.createScriptUseCase = createScriptUseCase;
        this.imageImporter = imageImporter;
        this.executors = executors;
    }

    /** 进入编辑页时加载剧本（scriptId 为 null 表示新建）。 */
    public void load(String scriptId) {
        if (scriptSource != null) {
            return;
        }
        if (scriptId != null) {
            scriptSource = scriptRepository.observeById(scriptId);
            scriptSource.observeForever(scriptObserver);
        }
    }

    public LiveData<Script> getScript() {
        return script;
    }

    public LiveData<String> getCoverRef() {
        return coverRef;
    }

    public LiveData<SingleEvent<String>> getEvents() {
        return events;
    }

    private void initCoverOnce(String ref) {
        if (coverInitialized) {
            return;
        }
        coverInitialized = true;
        coverRef.postValue(ref);
    }

    /** 导入封面图片（与角色头像同路径：解码采样后转 WebP 存 covers 目录）。 */
    public void importCover(Uri uri) {
        if (uri == null) {
            return;
        }
        executors.diskIO().execute(() -> {
            String ref = imageImporter.importImage(LocalAssetStore.DIR_COVERS, uri);
            if (ref != null) {
                coverInitialized = true;
                coverRef.postValue(ref);
            } else {
                events.postValue(new SingleEvent<>("error:cover"));
            }
        });
    }

    /** 清除封面（保存后生效）。 */
    public void clearCover() {
        coverInitialized = true;
        coverRef.postValue(null);
    }

    public void create(String name, String oneLine, long now) {
        executors.diskIO().execute(() -> {
            AppError[] error = new AppError[1];
            String id = createScriptUseCase.execute(name, oneLine, now, error);
            if (id != null) {
                String cover = coverRef.getValue();
                if (cover != null) {
                    // 新建剧本默认无封面字段，创建后立即补写封面引用。
                    Script created = scriptRepository.getById(id);
                    if (created != null) {
                        scriptRepository.updateScript(created.copyWith(created.getName(),
                                created.getOneLine(), cover, now, created.getSortIndex()));
                    }
                }
                events.postValue(new SingleEvent<>("created:" + id));
            } else if (error[0] != null) {
                events.postValue(new SingleEvent<>("error:" + error[0].getMessage()));
            }
        });
    }

    public void update(Script current, String name, String oneLine, long now) {
        executors.diskIO().execute(() -> {
            String cover = coverInitialized ? coverRef.getValue() : current.getCoverRef();
            Script updated = current.copyWith(name, oneLine, cover, now, current.getSortIndex());
            scriptRepository.updateScript(updated);
            events.postValue(new SingleEvent<>("saved"));
        });
    }

    @Override
    protected void onCleared() {
        if (scriptSource != null) {
            scriptSource.removeObserver(scriptObserver);
        }
        super.onCleared();
    }
}
