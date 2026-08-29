package com.example.roleplaychat.di;

import android.content.Context;

import com.example.roleplaychat.data.file.ImageImporter;
import com.example.roleplaychat.data.file.LocalAssetStore;
import com.example.roleplaychat.data.local.AppDatabase;
import com.example.roleplaychat.data.remote.OpenAiServiceFactory;
import com.example.roleplaychat.data.repository.AiRepositoryImpl;
import com.example.roleplaychat.data.repository.AppearanceRepositoryImpl;
import com.example.roleplaychat.data.repository.CharacterRepositoryImpl;
import com.example.roleplaychat.data.repository.ChatRepositoryImpl;
import com.example.roleplaychat.data.repository.ImportExportRepositoryImpl;
import com.example.roleplaychat.data.repository.ScriptRepositoryImpl;
import com.example.roleplaychat.data.repository.SettingsRepositoryImpl;
import com.example.roleplaychat.data.repository.WorldRepositoryImpl;
import com.example.roleplaychat.data.security.KeystoreSecretStore;
import com.example.roleplaychat.data.security.SecretStore;
import com.example.roleplaychat.domain.ai.AiTurnOrchestrator;
import com.example.roleplaychat.domain.repository.AiRepository;
import com.example.roleplaychat.domain.repository.AppearanceRepository;
import com.example.roleplaychat.domain.repository.CharacterRepository;
import com.example.roleplaychat.domain.repository.ChatRepository;
import com.example.roleplaychat.domain.repository.ImportExportRepository;
import com.example.roleplaychat.domain.repository.ScriptRepository;
import com.example.roleplaychat.domain.repository.SettingsRepository;
import com.example.roleplaychat.domain.repository.WorldRepository;
import com.example.roleplaychat.domain.usecase.AdvanceAiUseCase;
import com.example.roleplaychat.domain.usecase.CreateScriptUseCase;
import com.example.roleplaychat.domain.usecase.DeleteScriptUseCase;
import com.example.roleplaychat.domain.usecase.ExportDataUseCase;
import com.example.roleplaychat.domain.usecase.ImportDataUseCase;
import com.example.roleplaychat.domain.usecase.SaveCharacterUseCase;
import com.example.roleplaychat.domain.usecase.SendPlayerMessageUseCase;
import com.example.roleplaychat.domain.usecase.StopGenerationUseCase;
import com.example.roleplaychat.domain.usecase.SwitchIdentityUseCase;
import com.example.roleplaychat.util.AppExecutors;
import com.example.roleplaychat.util.Clock;
import com.example.roleplaychat.util.IdGenerator;

/**
 * 对象装配根（架构文档 §3.2：AppContainer 是对象装配根）。
 */
public class AppContainer {

    private final Context context;

    // 基础设施
    public final AppExecutors executors;
    public final Clock clock;
    public final IdGenerator idGenerator;

    // 数据
    public final AppDatabase database;
    public final SecretStore secretStore;
    public final LocalAssetStore assetStore;
    public final ImageImporter imageImporter;
    public final OpenAiServiceFactory aiServiceFactory;

    // 仓库（接口）
    public final ScriptRepository scriptRepository;
    public final WorldRepository worldRepository;
    public final CharacterRepository characterRepository;
    public final ChatRepository chatRepository;
    public final AppearanceRepository appearanceRepository;
    public final SettingsRepository settingsRepository;
    public final AiRepository aiRepository;
    public final ImportExportRepository importExportRepository;

    // AI 编排
    public final AiTurnOrchestrator aiOrchestrator;

    // 用例
    public final CreateScriptUseCase createScriptUseCase;
    public final DeleteScriptUseCase deleteScriptUseCase;
    public final SaveCharacterUseCase saveCharacterUseCase;
    public final SwitchIdentityUseCase switchIdentityUseCase;
    public final SendPlayerMessageUseCase sendPlayerMessageUseCase;
    public final AdvanceAiUseCase advanceAiUseCase;
    public final StopGenerationUseCase stopGenerationUseCase;
    public final ImportDataUseCase importDataUseCase;
    public final ExportDataUseCase exportDataUseCase;

    public AppContainer(Context context) {
        this.context = context.getApplicationContext();
        this.executors = new AppExecutors();
        this.clock = Clock.system();
        this.idGenerator = IdGenerator.random();

        this.database = AppDatabase.build(this.context);
        this.secretStore = new KeystoreSecretStore(this.context);
        this.assetStore = new LocalAssetStore(this.context);
        this.imageImporter = new ImageImporter(this.context, this.assetStore);

        this.settingsRepository = new SettingsRepositoryImpl(this.context, this.secretStore);
        this.aiServiceFactory = new OpenAiServiceFactory(settingsRepository.getApiConfig());
        settingsRepository.setApiConfigChangeListener(aiServiceFactory::updateConfig);
        this.aiRepository = new AiRepositoryImpl(aiServiceFactory);

        this.scriptRepository = new ScriptRepositoryImpl(database);
        this.worldRepository = new WorldRepositoryImpl(database);
        this.characterRepository = new CharacterRepositoryImpl(database);
        this.chatRepository = new ChatRepositoryImpl(database);
        this.appearanceRepository = new AppearanceRepositoryImpl(database);
        this.importExportRepository = new ImportExportRepositoryImpl(database, assetStore);

        this.aiOrchestrator = new AiTurnOrchestrator(
                scriptRepository, worldRepository, characterRepository, chatRepository,
                settingsRepository, aiRepository, idGenerator, "中文");

        this.createScriptUseCase = new CreateScriptUseCase(scriptRepository);
        this.deleteScriptUseCase = new DeleteScriptUseCase(scriptRepository);
        this.saveCharacterUseCase = new SaveCharacterUseCase(characterRepository);
        this.switchIdentityUseCase = new SwitchIdentityUseCase(scriptRepository, characterRepository);
        this.sendPlayerMessageUseCase = new SendPlayerMessageUseCase(
                scriptRepository, characterRepository, worldRepository, chatRepository,
                settingsRepository, aiOrchestrator, executors);
        this.advanceAiUseCase = new AdvanceAiUseCase(aiOrchestrator);
        this.stopGenerationUseCase = new StopGenerationUseCase(advanceAiUseCase);
        this.importDataUseCase = new ImportDataUseCase(importExportRepository);
        this.exportDataUseCase = new ExportDataUseCase(importExportRepository);
    }

    public Context context() {
        return context;
    }
}
