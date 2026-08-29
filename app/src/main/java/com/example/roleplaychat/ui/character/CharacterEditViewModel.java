package com.example.roleplaychat.ui.character;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.roleplaychat.data.file.ImageImporter;
import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.model.ApiConfig;
import com.example.roleplaychat.domain.model.AppErrorCode;
import com.example.roleplaychat.domain.model.PromptMessage;
import com.example.roleplaychat.domain.repository.AiRepository;
import com.example.roleplaychat.domain.repository.AiStreamListener;
import com.example.roleplaychat.domain.repository.SettingsRepository;
import com.example.roleplaychat.domain.repository.CharacterRepository;
import com.example.roleplaychat.domain.usecase.SaveCharacterUseCase;
import com.example.roleplaychat.ui.common.SingleEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色编辑 ViewModel。
 */
public class CharacterEditViewModel extends ViewModel {

    private final CharacterRepository characterRepository;
    private final SaveCharacterUseCase saveCharacterUseCase;
    private final ImageImporter imageImporter;
    private final com.example.roleplaychat.util.AppExecutors executors;
    private final AiRepository aiRepository;
    private final SettingsRepository settingsRepository;

    private final MutableLiveData<SingleEvent<String>> events = new MutableLiveData<>();
    private final MutableLiveData<String> avatarRef = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loaded = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> aiGenerating = new MutableLiveData<>(false);
    private final MutableLiveData<String> aiProgress = new MutableLiveData<>("");
    private final MutableLiveData<CharacterProfile> aiDraft = new MutableLiveData<>();
    private CharacterProfile editing;

    public CharacterEditViewModel(CharacterRepository characterRepository,
                                   SaveCharacterUseCase saveCharacterUseCase,
                                   ImageImporter imageImporter,
                                   AiRepository aiRepository,
                                   SettingsRepository settingsRepository,
                                   com.example.roleplaychat.util.AppExecutors executors) {
        this.characterRepository = characterRepository;
        this.saveCharacterUseCase = saveCharacterUseCase;
        this.imageImporter = imageImporter;
        this.aiRepository = aiRepository;
        this.settingsRepository = settingsRepository;
        this.executors = executors;
    }

    public LiveData<SingleEvent<String>> getEvents() {
        return events;
    }

    public LiveData<String> getAvatarRef() {
        return avatarRef;
    }

    public LiveData<Boolean> getLoaded() {
        return loaded;
    }

    public LiveData<Boolean> getAiGenerating() { return aiGenerating; }
    public LiveData<String> getAiProgress() { return aiProgress; }
    public LiveData<CharacterProfile> getAiDraft() { return aiDraft; }

    public void load(String scriptId, String characterId) {
        loadInternal(scriptId, characterId);
    }

    private void loadInternal(String scriptId, String characterId) {
        if (characterId == null) {
            // 新建：默认启用角色
            long now = System.currentTimeMillis();
            int sortIndex = characterRepository.nextSortIndex(scriptId);
            editing = new CharacterProfile(
                    java.util.UUID.randomUUID().toString(),
                    scriptId,
                    "",
                    new ArrayList<>(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new LinkedHashMap<>(),
                    new ArrayList<>(),
                    null,
                    null,
                    true,
                    sortIndex,
                    now,
                    now,
                    null);
            avatarRef.postValue(null);
        } else {
            editing = characterRepository.getById(characterId);
            avatarRef.postValue(editing == null ? null : editing.getAvatarRef());
        }
        loaded.postValue(true);
    }

    public CharacterProfile getEditing() {
        return editing;
    }

    public void aiEnhance(String description) {
        if (editing == null || description == null || description.trim().isEmpty()) {
            events.postValue(new SingleEvent<>("error:" + AppErrorCode.VALIDATION_FAILED.getCode()));
            return;
        }
        ApiConfig config = settingsRepository.getApiConfig();
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            events.postValue(new SingleEvent<>("error:" + AppErrorCode.AUTH_INVALID.getCode()));
            return;
        }
        aiGenerating.postValue(true);
        aiProgress.postValue("正在连接 AI…");
        List<PromptMessage> messages = new ArrayList<>();
        messages.add(PromptMessage.system("你是角色卡设计助手。只输出合法JSON，不要Markdown。字段：name字符串、aliases字符串数组、gender字符串、age字符串、personality字符串、backstory字符串、speaking_style字符串、catchphrases字符串数组、strengths字符串数组、flaws字符串数组、relationships对象、sample_lines字符串数组、system_prompt字符串、hidden_setting字符串。"));
        messages.add(PromptMessage.user("根据这段描述生成完整角色卡：" + description.trim()));
        String requestId = java.util.UUID.randomUUID().toString();
        aiRepository.streamPrompt(requestId, messages, config.getModel(),
                Math.max(config.getMaxTokens(), 4096),
                config.getTemperature(), config.getTopP(), new AiStreamListener() {
                    private int received;
                    @Override public void onStarted(String id) { aiProgress.postValue("AI 正在塑造角色…"); }
                    @Override public void onTextDelta(String id, String delta) {
                        received += delta == null ? 0 : delta.length();
                        aiProgress.postValue("正在生成角色…已接收 " + received + " 字");
                    }
                    @Override public void onCompleted(String id, String fullText) {
                        try {
                            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(fullText).getAsJsonObject();
                            CharacterProfile draft = new CharacterProfile(editing.getId(), editing.getScriptId(),
                                    string(json, "name"), list(json, "aliases"), avatarRef.getValue(),
                                    string(json, "gender"), string(json, "age"), string(json, "personality"),
                                    string(json, "backstory"), string(json, "speaking_style"),
                                    list(json, "catchphrases"), list(json, "strengths"), list(json, "flaws"),
                                    map(json, "relationships"), list(json, "sample_lines"),
                                    string(json, "system_prompt"), string(json, "hidden_setting"), true,
                                    editing.getSortIndex(), editing.getCreatedAt(), System.currentTimeMillis(),
                                    editing.getExtraJson());
                            editing = draft;
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

    public void importAvatar(android.net.Uri uri) {
        if (uri == null) {
            return;
        }
        executors.diskIO().execute(() -> {
            String ref = imageImporter.importImage(
                    com.example.roleplaychat.data.file.LocalAssetStore.DIR_AVATARS, uri);
            if (ref != null) {
                avatarRef.postValue(ref);
            } else {
                events.postValue(new SingleEvent<>("error:avatar"));
            }
        });
    }

    public void save(String name, String aliasesText, String gender, String age,
                     String personality, String backstory, String speakingStyle,
                     String catchphrasesText, String strengthsText, String flawsText,
                     String relationshipsText, String sampleLinesText, String systemPrompt,
                     String hiddenSetting, long now) {
        if (editing == null) {
            events.setValue(new SingleEvent<>("error:not_loaded"));
            return;
        }
        CharacterProfile updated = new CharacterProfile(
                editing.getId(),
                editing.getScriptId(),
                name,
                splitLines(aliasesText),
                avatarRef.getValue(),
                emptyToNull(gender),
                emptyToNull(age),
                emptyToNull(personality),
                emptyToNull(backstory),
                emptyToNull(speakingStyle),
                splitLines(catchphrasesText),
                splitLines(strengthsText),
                splitLines(flawsText),
                splitRelationships(relationshipsText),
                splitLines(sampleLinesText),
                emptyToNull(systemPrompt),
                emptyToNull(hiddenSetting),
                true,
                editing.getSortIndex(),
                editing.getCreatedAt(),
                now,
                editing.getExtraJson());
        CharacterProfile toSave = updated;
        executors.diskIO().execute(() -> {
            AppError error = saveCharacterUseCase.execute(toSave);
            if (error != null) {
                events.postValue(new SingleEvent<>("error:" + error.getMessage()));
            } else {
                events.postValue(new SingleEvent<>("saved"));
            }
        });
    }

    public void toggleEnabled(boolean enabled) {
        if (editing != null) {
            String characterId = editing.getId();
            executors.diskIO().execute(() -> {
                characterRepository.setEnabled(characterId, enabled, System.currentTimeMillis());
                events.postValue(new SingleEvent<>(enabled ? "enabled" : "disabled"));
            });
        }
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

    private Map<String, String> splitRelationships(String text) {
        Map<String, String> result = new LinkedHashMap<>();
        if (text == null || text.trim().isEmpty()) {
            return result;
        }
        for (String line : text.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // 支持格式：目标名：关系 或 目标名-关系
            int colon = trimmed.indexOf('：');
            if (colon < 0) {
                colon = trimmed.indexOf(':');
            }
            if (colon < 0) {
                colon = trimmed.indexOf('-');
            }
            if (colon > 0) {
                result.put(trimmed.substring(0, colon).trim(), trimmed.substring(colon + 1).trim());
            } else {
                result.put(trimmed, "");
            }
        }
        return result;
    }

    private String emptyToNull(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    private static String string(com.google.gson.JsonObject json, String name) {
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsString() : null;
    }
    private static List<String> list(com.google.gson.JsonObject json, String name) {
        List<String> values = new ArrayList<>();
        if (json.has(name) && json.get(name).isJsonArray()) for (com.google.gson.JsonElement e : json.getAsJsonArray(name)) values.add(e.getAsString());
        return values;
    }
    private static Map<String, String> map(com.google.gson.JsonObject json, String name) {
        Map<String, String> values = new LinkedHashMap<>();
        if (json.has(name) && json.get(name).isJsonObject()) for (Map.Entry<String, com.google.gson.JsonElement> e : json.getAsJsonObject(name).entrySet()) values.put(e.getKey(), e.getValue().getAsString());
        return values;
    }
}
