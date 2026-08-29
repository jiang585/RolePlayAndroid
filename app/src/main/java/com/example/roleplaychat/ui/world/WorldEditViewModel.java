package com.example.roleplaychat.ui.world;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.roleplaychat.domain.model.ApiConfig;
import com.example.roleplaychat.domain.model.AppErrorCode;
import com.example.roleplaychat.domain.model.PromptMessage;
import com.example.roleplaychat.domain.repository.AiRepository;
import com.example.roleplaychat.domain.repository.AiStreamListener;
import com.example.roleplaychat.domain.model.WorldSetting;
import com.example.roleplaychat.domain.repository.ScriptRepository;
import com.example.roleplaychat.domain.repository.SettingsRepository;
import com.example.roleplaychat.domain.repository.WorldRepository;
import com.example.roleplaychat.ui.common.SingleEvent;
import com.example.roleplaychat.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;

/**
 * 世界观编辑 ViewModel（FR-201~204）。DB 操作在后台线程（§3.2）。
 */
public class WorldEditViewModel extends ViewModel {

    private final WorldRepository worldRepository;
    private final ScriptRepository scriptRepository;
    private final AiRepository aiRepository;
    private final SettingsRepository settingsRepository;
    private final AppExecutors executors;

    private final MutableLiveData<SingleEvent<String>> events = new MutableLiveData<>();
    private final MutableLiveData<Boolean> aiGenerating = new MutableLiveData<>(false);
    private final MutableLiveData<String> aiProgress = new MutableLiveData<>("");
    private final MutableLiveData<WorldSetting> aiDraft = new MutableLiveData<>();
    private String scriptId;

    public WorldEditViewModel(WorldRepository worldRepository,
                              ScriptRepository scriptRepository,
                               AiRepository aiRepository,
                              SettingsRepository settingsRepository,
                              AppExecutors executors) {
        this.worldRepository = worldRepository;
        this.scriptRepository = scriptRepository;
        this.aiRepository = aiRepository;
        this.settingsRepository = settingsRepository;
        this.executors = executors;
    }

    public void setScriptId(String scriptId) {
        this.scriptId = scriptId;
    }

    public LiveData<WorldSetting> getWorld() {
        return worldRepository.observeByScriptId(scriptId == null ? "" : scriptId);
    }

    public LiveData<SingleEvent<String>> getEvents() {
        return events;
    }

    public LiveData<Boolean> getAiGenerating() {
        return aiGenerating;
    }

    public LiveData<String> getAiProgress() { return aiProgress; }

    public LiveData<WorldSetting> getAiDraft() { return aiDraft; }

    public void save(String era, String location, String factionsText, String rulesText,
                     String storyHook, String background, String tagsText, String versionNote, long now) {
        if (scriptId == null) {
            events.postValue(new SingleEvent<>("error:no_script"));
            return;
        }
        executors.diskIO().execute(() -> {
            WorldSetting current = worldRepository.getByScriptId(scriptId);
            WorldSetting updated = new WorldSetting(
                    current == null ? java.util.UUID.randomUUID().toString() : current.getId(),
                    scriptId,
                    emptyToNull(era),
                    emptyToNull(location),
                    splitLines(factionsText),
                    splitLines(rulesText),
                    emptyToNull(storyHook),
                    emptyToNull(background),
                    splitLines(tagsText),
                    emptyToNull(versionNote),
                    now);
            worldRepository.save(updated);
            scriptRepository.touchUpdatedAt(scriptId, now);
            events.postValue(new SingleEvent<>("saved"));
        });
    }

    /**
     * AI 完善世界观：把用户一句话描述作为输入，请求 AI 生成草稿。
     * 简化实现：将生成结果作为完整背景文本回填（由用户检查后保存）。
     */
    public void aiEnhance(String userDescription) {
        if (userDescription == null || userDescription.trim().isEmpty()) {
            events.postValue(new SingleEvent<>("error:empty_desc"));
            return;
        }
        if (scriptId == null) {
            events.postValue(new SingleEvent<>("error:no_script"));
            return;
        }
        ApiConfig config = settingsRepository.getApiConfig();
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            events.postValue(new SingleEvent<>("error:" + AppErrorCode.AUTH_INVALID.getCode()));
            return;
        }
        aiGenerating.postValue(true);
        aiProgress.postValue("正在连接 AI…");
        String requestId = java.util.UUID.randomUUID().toString();
        List<PromptMessage> messages = new ArrayList<>();
        messages.add(PromptMessage.system("你是世界观设计助手。只输出合法JSON，不要Markdown。字段必须为：era字符串、location字符串、factions字符串数组、rules字符串数组、story_hook字符串、background_full字符串、tags字符串数组、version_note字符串。"));
        messages.add(PromptMessage.user("根据这段描述生成完整世界观：" + userDescription.trim()));
        aiRepository.streamPrompt(requestId, messages, config.getModel(),
                Math.max(config.getMaxTokens(), 4096),
                config.getTemperature(), config.getTopP(), new AiStreamListener() {
                    private int received;
                    @Override public void onStarted(String id) { aiProgress.postValue("AI 正在构思世界观…"); }
                    @Override public void onTextDelta(String id, String delta) {
                        received += delta == null ? 0 : delta.length();
                        aiProgress.postValue("正在生成世界观…已接收 " + received + " 字");
                    }
                    @Override public void onCompleted(String id, String fullText) {
                        try {
                            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(fullText).getAsJsonObject();
                            WorldSetting draft = new WorldSetting(java.util.UUID.randomUUID().toString(), scriptId,
                                    string(json, "era"), string(json, "location"), list(json, "factions"),
                                    list(json, "rules"), string(json, "story_hook"),
                                    string(json, "background_full"), list(json, "tags"),
                                    string(json, "version_note"), System.currentTimeMillis());
                            aiDraft.postValue(draft);
                            events.postValue(new SingleEvent<>("ai_done"));
                        } catch (RuntimeException e) {
                            events.postValue(new SingleEvent<>("error:" + AppErrorCode.OUTPUT_INVALID.getCode()));
                        } finally {
                            aiGenerating.postValue(false);
                            aiProgress.postValue("");
                        }
                    }
                    @Override public void onFailed(String id, AppErrorCode code, String message) {
                        aiGenerating.postValue(false);
                        aiProgress.postValue("");
                        events.postValue(new SingleEvent<>("error:" + (code == null ? AppErrorCode.UNKNOWN.getCode() : code.getCode())));
                    }
                });
    }

    private static String string(com.google.gson.JsonObject json, String name) {
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsString() : null;
    }

    private static List<String> list(com.google.gson.JsonObject json, String name) {
        List<String> values = new ArrayList<>();
        if (json.has(name) && json.get(name).isJsonArray()) {
            for (com.google.gson.JsonElement item : json.getAsJsonArray(name)) values.add(item.getAsString());
        }
        return values;
    }

    private List<String> splitLines(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return result;
        }
        for (String line : text.split("\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String emptyToNull(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }
}
