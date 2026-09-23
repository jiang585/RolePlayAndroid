package com.example.roleplaychat.ui.character;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.roleplaychat.data.file.ImageImporter;
import com.example.roleplaychat.data.file.LocalAssetStore;
import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.model.CharacterVisualAsset;
import com.example.roleplaychat.domain.model.CharacterVisualProfile;
import com.example.roleplaychat.domain.model.ApiConfig;
import com.example.roleplaychat.domain.model.AppErrorCode;
import com.example.roleplaychat.domain.model.PromptMessage;
import com.example.roleplaychat.domain.repository.AiRepository;
import com.example.roleplaychat.domain.repository.AiStreamListener;
import com.example.roleplaychat.domain.repository.SettingsRepository;
import com.example.roleplaychat.domain.repository.CharacterRepository;
import com.example.roleplaychat.domain.repository.CharacterVisualRepository;
import com.example.roleplaychat.domain.repository.ScriptRepository;
import com.example.roleplaychat.domain.repository.ImageGenerationGateway;
import com.example.roleplaychat.domain.model.ImageGenerationRequest;
import com.example.roleplaychat.domain.model.ImageGenerationStatus;
import com.example.roleplaychat.domain.model.Script;
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
    private final CharacterVisualRepository visualRepository;
    private final ScriptRepository scriptRepository;
    private final LocalAssetStore assetStore;
    private final ImageGenerationGateway imageGateway;
    private final MutableLiveData<Boolean> visualGenerating = new MutableLiveData<>(false);
    private final MutableLiveData<CharacterVisualProfile> visualProfile = new MutableLiveData<>();
    private android.net.Uri pendingFaceUri;
    private String visualDescription;
    private String heightText;
    private String bodyType;

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
                                   com.example.roleplaychat.util.AppExecutors executors,
                                   CharacterVisualRepository visualRepository,
                                   ScriptRepository scriptRepository,
                                   LocalAssetStore assetStore,
                                   ImageGenerationGateway imageGateway) {
        this.characterRepository = characterRepository;
        this.saveCharacterUseCase = saveCharacterUseCase;
        this.imageImporter = imageImporter;
        this.aiRepository = aiRepository;
        this.settingsRepository = settingsRepository;
        this.executors = executors;
        this.visualRepository = visualRepository;
        this.scriptRepository = scriptRepository;
        this.assetStore = assetStore;
        this.imageGateway = imageGateway;
    }

    public CharacterEditViewModel(CharacterRepository characterRepository,
                                   SaveCharacterUseCase saveCharacterUseCase,
                                   ImageImporter imageImporter, AiRepository aiRepository,
                                   SettingsRepository settingsRepository,
                                   com.example.roleplaychat.util.AppExecutors executors) {
        this(characterRepository, saveCharacterUseCase, imageImporter, aiRepository,
                settingsRepository, executors, null, null, null, null);
    }

    public LiveData<CharacterVisualProfile> getVisualProfile() { return visualProfile; }
    public LiveData<Boolean> getVisualGenerating() { return visualGenerating; }
    public void selectVisualCandidate(int index) {
        CharacterVisualProfile current = visualProfile.getValue();
        if (current == null || index < 0 || index >= current.getAssets().size() || visualRepository == null) return;
        java.util.List<CharacterVisualAsset> assets = new java.util.ArrayList<>();
        for (int i = 0; i < current.getAssets().size(); i++) {
            CharacterVisualAsset a = current.getAssets().get(i);
            assets.add(new CharacterVisualAsset(a.getId(), a.getProfileId(), a.getLocalPath(), a.getSha256(),
                    a.getAssetType(), i == index, a.getWidth(), a.getHeight(), a.getCreatedAt()));
        }
        CharacterVisualProfile updated = new CharacterVisualProfile(current.getId(), current.getCharacterId(),
                current.getStatus(), current.getSource(), current.getVersion(), current.getIdentityPrompt(),
                current.getAppearanceJson(), current.getNegativePrompt(), current.getCreatedAt(),
                System.currentTimeMillis(), assets);
        visualRepository.save(updated); visualProfile.postValue(updated);
    }
    public void setFaceUri(android.net.Uri uri) { pendingFaceUri = uri; }
    public void setVisualFields(String description, String height, String body) {
        visualDescription = description; heightText = height; bodyType = body;
    }

    /** 生成三张身份候选；首张先作为当前主图，用户可在视觉设定卡片中替换。 */
    public void generateVisualCandidates(String prompt) {
        if (editing == null || characterRepository.getById(editing.getId()) == null
                || imageGateway == null || visualRepository == null || assetStore == null) {
            events.postValue(new SingleEvent<>("error:save_before_visual"));
            return;
        }
        visualGenerating.postValue(true);
        executors.networkIO().execute(() -> {
            try {
                java.util.List<CharacterVisualAsset> assets = new java.util.ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    String clientJob = "identity-" + editing.getId() + "-" + i + "-" + System.nanoTime();
                    ImageGenerationRequest request = new ImageGenerationRequest(clientJob, editing.getScriptId(), editing.getId(),
                            ImageGenerationRequest.Model.ZIMAGE, ImageGenerationRequest.Mode.TXT2IMG,
                            "正面人物证件式肖像，" + (prompt == null ? "自然表情，清晰五官" : prompt.trim()), "",
                            java.util.Collections.emptyList(), 768, 1024, Math.abs(System.nanoTime()), "VISUAL_PROFILE_SETUP");
                    ImageGenerationStatus created = imageGateway.create(request, new String[0]);
                    ImageGenerationStatus ready = null;
                    for (int poll = 0; poll < 300; poll++) {
                        ready = imageGateway.status(created.getJobId());
                        if (ready.getState() == ImageGenerationStatus.State.READY) break;
                        if (ready.getState() == ImageGenerationStatus.State.FAILED_FINAL) throw new IllegalStateException("Huajing 生成失败");
                        Thread.sleep(1200L);
                    }
                    if (ready == null || ready.getResultAssetId() == null) throw new IllegalStateException("Huajing 生成超时");
                    java.io.File tmp = new java.io.File(assetStore.tmpDir(), "identity_" + editing.getId() + "_" + i + ".png");
                    imageGateway.download(ready.getResultAssetId(), tmp);
                    String ref;
                    try (java.io.FileInputStream in = new java.io.FileInputStream(tmp)) {
                        ref = assetStore.storeStream(LocalAssetStore.DIR_IDENTITIES, tmp.getName(), ".png", in);
                    }
                    tmp.delete();
                    assets.add(new CharacterVisualAsset(java.util.UUID.randomUUID().toString(),
                            java.util.UUID.randomUUID().toString(), ref, null, "FRONT_FACE_CANDIDATE", i == 0, 768, 1024,
                            System.currentTimeMillis()));
                }
                String profileId = java.util.UUID.randomUUID().toString();
                java.util.List<CharacterVisualAsset> fixed = new java.util.ArrayList<>();
                for (CharacterVisualAsset asset : assets) fixed.add(new CharacterVisualAsset(asset.getId(), profileId,
                        asset.getLocalPath(), asset.getSha256(), asset.getAssetType(), asset.isPrimary(), asset.getWidth(), asset.getHeight(), asset.getCreatedAt()));
                CharacterVisualProfile profile = new CharacterVisualProfile(profileId, editing.getId(),
                        CharacterVisualProfile.Status.READY, CharacterVisualProfile.Source.GENERATED, 1,
                        prompt, "身高：" + safe(heightText) + "；体型：" + safe(bodyType), "", System.currentTimeMillis(),
                        System.currentTimeMillis(), fixed);
                visualRepository.save(profile);
                visualProfile.postValue(profile);
            } catch (Exception error) {
                events.postValue(new SingleEvent<>("error:visual_generate"));
            } finally { visualGenerating.postValue(false); }
        });
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
            if (visualRepository != null) visualProfile.postValue(visualRepository.getByCharacterId(characterId));
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
            Script scriptBeforeSave = scriptRepository == null ? null : scriptRepository.getById(toSave.getScriptId());
            CharacterVisualProfile priorVisual = visualRepository == null ? null : visualRepository.getByCharacterId(toSave.getId());
            boolean existingCharacter = characterRepository.getById(toSave.getId()) != null;
            if (existingCharacter && scriptBeforeSave != null && scriptBeforeSave.isVisual()
                    && pendingFaceUri == null && (priorVisual == null || !priorVisual.isReady())) {
                events.postValue(new SingleEvent<>("error:face_required"));
                return;
            }
            if (existingCharacter && scriptBeforeSave != null && scriptBeforeSave.isVisual()
                    && (heightText == null || heightText.trim().isEmpty()
                    || bodyType == null || bodyType.trim().isEmpty())) {
                events.postValue(new SingleEvent<>("error:measurements_required"));
                return;
            }
            AppError error = saveCharacterUseCase.execute(toSave);
            if (error != null) {
                events.postValue(new SingleEvent<>("error:" + error.getMessage()));
            } else {
                if (visualRepository != null && scriptRepository != null) {
                    CharacterVisualProfile existing = visualRepository.getByCharacterId(toSave.getId());
                    String faceRef = pendingFaceUri == null ? null : imageImporter.importImage(LocalAssetStore.DIR_IDENTITIES, pendingFaceUri);
                    if (pendingFaceUri != null && faceRef == null) {
                        events.postValue(new SingleEvent<>("error:face"));
                        return;
                    }
                    java.util.List<CharacterVisualAsset> assets = faceRef == null
                            ? (existing == null ? java.util.Collections.emptyList() : existing.getAssets())
                            : java.util.Collections.singletonList(new CharacterVisualAsset(
                                    java.util.UUID.randomUUID().toString(),
                                    existing == null ? java.util.UUID.randomUUID().toString() : existing.getId(),
                                    faceRef, null, "FRONT_FACE", true, 0, 0, now));
                    boolean hasFields = (visualDescription != null && !visualDescription.trim().isEmpty())
                            || (heightText != null && !heightText.trim().isEmpty())
                            || (bodyType != null && !bodyType.trim().isEmpty());
                    if (faceRef != null || existing != null || hasFields) {
                        String profileId = existing == null ? (assets.isEmpty() ? java.util.UUID.randomUUID().toString()
                                : assets.get(0).getProfileId()) : existing.getId();
                        String appearance = "身高：" + safe(heightText) + "；体型：" + safe(bodyType);
                        CharacterVisualProfile profile = new CharacterVisualProfile(profileId, toSave.getId(),
                                assets.isEmpty() ? CharacterVisualProfile.Status.NEEDS_SETUP : CharacterVisualProfile.Status.READY,
                                faceRef == null && existing != null ? existing.getSource() : CharacterVisualProfile.Source.UPLOAD,
                                existing == null ? 1 : existing.getVersion() + 1,
                                safe(visualDescription), appearance, "", existing == null ? now : existing.getCreatedAt(), now, assets);
                        visualRepository.save(profile);
                    }
                }
                events.postValue(new SingleEvent<>("saved"));
            }
        });
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }

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
