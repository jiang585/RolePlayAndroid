package com.example.roleplaychat.di;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.roleplaychat.ui.appearance.AppearanceViewModel;
import com.example.roleplaychat.ui.character.CharacterEditViewModel;
import com.example.roleplaychat.ui.character.CharacterImportViewModel;
import com.example.roleplaychat.ui.chat.ChatViewModel;
import com.example.roleplaychat.ui.identity.IdentityViewModel;
import com.example.roleplaychat.ui.script.ScriptEditViewModel;
import com.example.roleplaychat.ui.script.ScriptListViewModel;
import com.example.roleplaychat.ui.settings.SettingsViewModel;
import com.example.roleplaychat.ui.world.WorldEditViewModel;

/**
 * ViewModel 构造分发（架构文档 §3.2：Fragment 通过统一 ViewModelFactory 获取依赖）。
 */
public class ViewModelFactory implements ViewModelProvider.Factory {

    private final AppContainer container;

    public ViewModelFactory(AppContainer container) {
        this.container = container;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ScriptListViewModel.class)) {
            return (T) new ScriptListViewModel(container.scriptRepository,
                    container.createScriptUseCase, container.deleteScriptUseCase, container.executors);
        }
        if (modelClass.isAssignableFrom(ScriptEditViewModel.class)) {
            return (T) new ScriptEditViewModel(container.scriptRepository,
                    container.createScriptUseCase, container.executors);
        }
        if (modelClass.isAssignableFrom(WorldEditViewModel.class)) {
            return (T) new WorldEditViewModel(container.worldRepository,
                    container.scriptRepository, container.aiRepository,
                    container.settingsRepository, container.executors);
        }
        if (modelClass.isAssignableFrom(CharacterEditViewModel.class)) {
            return (T) new CharacterEditViewModel(container.characterRepository,
                    container.saveCharacterUseCase, container.imageImporter, container.aiRepository,
                    container.settingsRepository, container.executors);
        }
        if (modelClass.isAssignableFrom(CharacterImportViewModel.class)) {
            return (T) new CharacterImportViewModel(container.importDataUseCase, container.executors);
        }
        if (modelClass.isAssignableFrom(IdentityViewModel.class)) {
            return (T) new IdentityViewModel(container.scriptRepository,
                    container.characterRepository, container.switchIdentityUseCase,
                    container.clock, container.executors);
        }
        if (modelClass.isAssignableFrom(ChatViewModel.class)) {
            return (T) new ChatViewModel(
                    container.scriptRepository,
                    container.characterRepository,
                    container.chatRepository,
                    container.appearanceRepository,
                    container.sendPlayerMessageUseCase,
                    container.advanceAiUseCase,
                    container.stopGenerationUseCase,
                    container.executors);
        }
        if (modelClass.isAssignableFrom(AppearanceViewModel.class)) {
            return (T) new AppearanceViewModel(container.appearanceRepository,
                    container.characterRepository, container.imageImporter, container.executors);
        }
        if (modelClass.isAssignableFrom(SettingsViewModel.class)) {
            return (T) new SettingsViewModel(container.settingsRepository,
                    container.aiRepository, container.scriptRepository, container.executors);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
