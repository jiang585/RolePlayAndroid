package com.example.roleplaychat.domain.model;

import androidx.annotation.Nullable;

public final class ImageGenerationStatus {
    public enum State { CREATED, UPLOADING_REFERENCES, QUEUED, RUNNING, DOWNLOADING, READY, FAILED_RETRYABLE, FAILED_FINAL, CANCELLED, EXPIRED }
    private final String jobId;
    private final State state;
    private final String stage;
    private final float progress;
    @Nullable private final String resultAssetId;
    @Nullable private final String errorCode;
    public ImageGenerationStatus(String jobId, State state, String stage, float progress,
                                 @Nullable String resultAssetId, @Nullable String errorCode) {
        this.jobId = jobId; this.state = state; this.stage = stage; this.progress = progress;
        this.resultAssetId = resultAssetId; this.errorCode = errorCode;
    }
    public String getJobId() { return jobId; }
    public State getState() { return state; }
    public String getStage() { return stage; }
    public float getProgress() { return progress; }
    @Nullable public String getResultAssetId() { return resultAssetId; }
    @Nullable public String getErrorCode() { return errorCode; }
}
