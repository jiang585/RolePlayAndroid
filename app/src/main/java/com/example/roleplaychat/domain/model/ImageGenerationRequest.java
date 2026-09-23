package com.example.roleplaychat.domain.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ImageGenerationRequest {
    public enum Model { QWEN_IMAGE_2_1, ZIMAGE }
    public enum Mode { TXT2IMG, EDIT, MULTIREF }

    private final String clientJobId;
    private final String scriptId;
    @Nullable private final String characterId;
    private final Model model;
    private final Mode mode;
    private final String prompt;
    private final String negativePrompt;
    private final List<String> referencePaths;
    private final int width;
    private final int height;
    private final long seed;
    private final String trigger;
    public ImageGenerationRequest(String clientJobId, String scriptId, @Nullable String characterId,
                                  Model model, Mode mode, String prompt, String negativePrompt,
                                  List<String> referencePaths, int width, int height, long seed, String trigger) {
        this.clientJobId = Objects.requireNonNull(clientJobId); this.scriptId = Objects.requireNonNull(scriptId);
        this.characterId = characterId; this.model = Objects.requireNonNull(model); this.mode = Objects.requireNonNull(mode);
        this.prompt = Objects.requireNonNull(prompt); this.negativePrompt = negativePrompt == null ? "" : negativePrompt;
        this.referencePaths = referencePaths == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(referencePaths));
        this.width = width; this.height = height; this.seed = seed; this.trigger = trigger == null ? "MANUAL_USER_REQUEST" : trigger;
    }
    public String getClientJobId() { return clientJobId; }
    public String getScriptId() { return scriptId; }
    @Nullable public String getCharacterId() { return characterId; }
    public Model getModel() { return model; }
    public Mode getMode() { return mode; }
    public String getPrompt() { return prompt; }
    public String getNegativePrompt() { return negativePrompt; }
    public List<String> getReferencePaths() { return referencePaths; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public long getSeed() { return seed; }
    public String getTrigger() { return trigger; }
}
