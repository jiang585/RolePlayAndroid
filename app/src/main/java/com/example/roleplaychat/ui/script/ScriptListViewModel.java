package com.example.roleplaychat.ui.script;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.Script;
import com.example.roleplaychat.domain.repository.ScriptRepository;
import com.example.roleplaychat.domain.usecase.CreateScriptUseCase;
import com.example.roleplaychat.domain.usecase.DeleteScriptUseCase;
import com.example.roleplaychat.ui.common.SingleEvent;
import com.example.roleplaychat.util.AppExecutors;

import java.util.List;

/**
 * 剧本列表 ViewModel（架构文档 §10.2）。DB 操作在后台线程（§3.2）。
 */
public class ScriptListViewModel extends ViewModel {

    private final ScriptRepository scriptRepository;
    private final CreateScriptUseCase createScriptUseCase;
    private final DeleteScriptUseCase deleteScriptUseCase;
    private final AppExecutors executors;

    private final MutableLiveData<List<Script>> scripts = new MutableLiveData<>();
    private final MutableLiveData<SingleEvent<String>> events = new MutableLiveData<>();
    private final LiveData<List<Script>> scriptsSource;
    private final Observer<List<Script>> scriptsObserver = scripts::postValue;

    public ScriptListViewModel(ScriptRepository scriptRepository,
                               CreateScriptUseCase createScriptUseCase,
                               DeleteScriptUseCase deleteScriptUseCase,
                               AppExecutors executors) {
        this.scriptRepository = scriptRepository;
        this.createScriptUseCase = createScriptUseCase;
        this.deleteScriptUseCase = deleteScriptUseCase;
        this.executors = executors;
        scriptsSource = scriptRepository.observeAll();
        scriptsSource.observeForever(scriptsObserver);
        executors.diskIO().execute(() -> scripts.postValue(scriptRepository.getAll()));
    }

    public LiveData<List<Script>> getScripts() {
        return scripts;
    }

    public LiveData<SingleEvent<String>> getEvents() {
        return events;
    }

    public void createScript(String name, String oneLine, long now) {
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

    public void deleteScript(Script script) {
        executors.diskIO().execute(() -> deleteScriptUseCase.execute(script.getId()));
    }

    @Override
    protected void onCleared() {
        scriptsSource.removeObserver(scriptsObserver);
        super.onCleared();
    }
}
