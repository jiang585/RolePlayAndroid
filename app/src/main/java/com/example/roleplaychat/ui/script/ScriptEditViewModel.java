package com.example.roleplaychat.ui.script;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.SavedStateHandle;

import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.Script;
import com.example.roleplaychat.domain.repository.ScriptRepository;
import com.example.roleplaychat.domain.usecase.CreateScriptUseCase;
import com.example.roleplaychat.ui.common.SingleEvent;
import com.example.roleplaychat.util.AppExecutors;

/**
 * 剧本编辑 ViewModel（新建或编辑）。DB 操作在后台线程（§3.2）。
 */
public class ScriptEditViewModel extends ViewModel {

    private final ScriptRepository scriptRepository;
    private final CreateScriptUseCase createScriptUseCase;
    private final AppExecutors executors;
    private final MutableLiveData<SingleEvent<String>> events = new MutableLiveData<>();
    private final LiveData<Script> script;

    public ScriptEditViewModel(ScriptRepository scriptRepository,
                               CreateScriptUseCase createScriptUseCase,
                               AppExecutors executors) {
        this(scriptRepository, createScriptUseCase, null, executors);
    }

    public ScriptEditViewModel(ScriptRepository scriptRepository,
                               CreateScriptUseCase createScriptUseCase,
                               SavedStateHandle savedStateHandle,
                               AppExecutors executors) {
        this.scriptRepository = scriptRepository;
        this.createScriptUseCase = createScriptUseCase;
        this.executors = executors;
        if (savedStateHandle != null && savedStateHandle.contains("scriptId")) {
            String scriptId = savedStateHandle.get("scriptId");
            this.script = scriptRepository.observeById(scriptId);
        } else {
            this.script = null;
        }
    }

    public LiveData<Script> getScript() {
        return script;
    }

    public LiveData<SingleEvent<String>> getEvents() {
        return events;
    }

    public void create(String name, String oneLine, long now) {
        executors.diskIO().execute(() -> {
            AppError[] error = new AppError[1];
            String id = createScriptUseCase.execute(name, oneLine, now, error);
            if (id != null) {
                events.postValue(new SingleEvent<>("created:" + id));
            } else if (error[0] != null) {
                events.postValue(new SingleEvent<>("error:" + error[0].getMessage()));
            }
        });
    }

    public void update(Script current, String name, String oneLine, long now) {
        executors.diskIO().execute(() -> {
            Script updated = current.copyWith(name, oneLine, current.getCoverRef(), now, current.getSortIndex());
            scriptRepository.updateScript(updated);
            events.postValue(new SingleEvent<>("saved"));
        });
    }
}
