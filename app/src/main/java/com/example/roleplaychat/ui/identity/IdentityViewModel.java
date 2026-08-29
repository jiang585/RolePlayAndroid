package com.example.roleplaychat.ui.identity;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.model.PlayerIdentity;
import com.example.roleplaychat.domain.repository.CharacterRepository;
import com.example.roleplaychat.domain.repository.ScriptRepository;
import com.example.roleplaychat.domain.usecase.SwitchIdentityUseCase;
import com.example.roleplaychat.ui.common.SingleEvent;
import com.example.roleplaychat.util.AppExecutors;
import com.example.roleplaychat.util.Clock;

import java.util.List;

/**
 * 身份选择 ViewModel（FR-401~404）。DB 操作在后台线程（§3.2）。
 */
public class IdentityViewModel extends ViewModel {

    private final ScriptRepository scriptRepository;
    private final CharacterRepository characterRepository;
    private final SwitchIdentityUseCase switchIdentityUseCase;
    private final Clock clock;
    private final AppExecutors executors;

    private final MutableLiveData<SingleEvent<String>> events = new MutableLiveData<>();
    private String scriptId;

    public IdentityViewModel(ScriptRepository scriptRepository,
                             CharacterRepository characterRepository,
                             SwitchIdentityUseCase switchIdentityUseCase,
                             Clock clock,
                             AppExecutors executors) {
        this.scriptRepository = scriptRepository;
        this.characterRepository = characterRepository;
        this.switchIdentityUseCase = switchIdentityUseCase;
        this.clock = clock;
        this.executors = executors;
    }

    public void setScriptId(String scriptId) {
        this.scriptId = scriptId;
    }

    public LiveData<List<CharacterProfile>> getEnabledCharacters() {
        return characterRepository.observeByScriptId(scriptId == null ? "" : scriptId);
    }

    public LiveData<PlayerIdentity> getCurrentIdentity() {
        return scriptRepository.observePlayerIdentity(scriptId == null ? "" : scriptId);
    }

    public LiveData<SingleEvent<String>> getEvents() {
        return events;
    }

    public void switchIdentity(PlayerIdentity.RoleType roleType, String characterId) {
        executors.diskIO().execute(() -> {
            AppError[] error = new AppError[1];
            PlayerIdentity identity = switchIdentityUseCase.execute(
                    scriptId, roleType, characterId, clock.currentTimeMillis(), error);
            if (identity != null) {
                events.postValue(new SingleEvent<>("saved"));
            } else if (error[0] != null) {
                events.postValue(new SingleEvent<>("error:" + error[0].getMessage()));
            }
        });
    }
}
