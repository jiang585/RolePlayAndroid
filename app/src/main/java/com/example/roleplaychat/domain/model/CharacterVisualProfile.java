package com.example.roleplaychat.domain.model;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CharacterVisualProfile {
    public enum Status { NEEDS_SETUP, READY, FAILED }
    public enum Source { UPLOAD, GENERATED }

    private final String id;
    private final String characterId;
    private final Status status;
    private final Source source;
    private final int version;
    @Nullable private final String identityPrompt;
    @Nullable private final String appearanceJson;
    @Nullable private final String negativePrompt;
    private final long createdAt;
    private final long updatedAt;
    private final List<CharacterVisualAsset> assets;

    public CharacterVisualProfile(String id, String characterId, Status status, Source source, int version,
                                  @Nullable String identityPrompt, @Nullable String appearanceJson,
                                  @Nullable String negativePrompt, long createdAt, long updatedAt,
                                  List<CharacterVisualAsset> assets) {
        this.id = Objects.requireNonNull(id); this.characterId = Objects.requireNonNull(characterId);
        this.status = status == null ? Status.NEEDS_SETUP : status;
        this.source = source == null ? Source.UPLOAD : source;
        this.version = version; this.identityPrompt = identityPrompt; this.appearanceJson = appearanceJson;
        this.negativePrompt = negativePrompt; this.createdAt = createdAt; this.updatedAt = updatedAt;
        this.assets = assets == null ? Collections.emptyList() : Collections.unmodifiableList(assets);
    }

    public String getId() { return id; }
    public String getCharacterId() { return characterId; }
    public Status getStatus() { return status; }
    public Source getSource() { return source; }
    public int getVersion() { return version; }
    @Nullable public String getIdentityPrompt() { return identityPrompt; }
    @Nullable public String getAppearanceJson() { return appearanceJson; }
    @Nullable public String getNegativePrompt() { return negativePrompt; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public List<CharacterVisualAsset> getAssets() { return assets; }
    public boolean isReady() { return status == Status.READY && !assets.isEmpty(); }
}
