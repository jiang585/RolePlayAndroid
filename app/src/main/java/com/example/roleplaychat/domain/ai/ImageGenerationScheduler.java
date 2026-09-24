package com.example.roleplaychat.domain.ai;

import androidx.annotation.Nullable;

import com.example.roleplaychat.data.file.LocalAssetStore;
import com.example.roleplaychat.data.local.dao.ImageGenerationJobDao;
import com.example.roleplaychat.data.local.dao.MessageAttachmentDao;
import com.example.roleplaychat.data.local.dao.MessageDao;
import com.example.roleplaychat.data.local.entity.ImageGenerationJobEntity;
import com.example.roleplaychat.data.local.entity.MessageAttachmentEntity;
import com.example.roleplaychat.domain.model.AiImageAction;
import com.example.roleplaychat.domain.model.CharacterVisualAsset;
import com.example.roleplaychat.domain.model.CharacterVisualProfile;
import com.example.roleplaychat.domain.model.ImageGenerationRequest;
import com.example.roleplaychat.domain.model.ImageGenerationStatus;
import com.example.roleplaychat.domain.model.Script;
import com.example.roleplaychat.domain.repository.CharacterVisualRepository;
import com.example.roleplaychat.domain.repository.ImageGenerationGateway;
import com.example.roleplaychat.domain.repository.ScriptRepository;
import com.example.roleplaychat.domain.repository.ChatRepository;
import com.example.roleplaychat.util.AppExecutors;
import com.example.roleplaychat.util.IdGenerator;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;

/** Queues image actions without holding up the conversational AI turn. */
public final class ImageGenerationScheduler {
    private final ScriptRepository scriptRepository;
    private final CharacterVisualRepository visualRepository;
    private final ImageGenerationGateway gateway;
    private final ImageGenerationJobDao jobDao;
    private final MessageAttachmentDao attachmentDao;
    private final MessageDao messageDao;
    private final ChatRepository chatRepository;
    private final LocalAssetStore assetStore;
    private final AppExecutors executors;
    private final IdGenerator idGenerator;

    public ImageGenerationScheduler(ScriptRepository scriptRepository, CharacterVisualRepository visualRepository,
                                    ImageGenerationGateway gateway, ImageGenerationJobDao jobDao,
                                    MessageAttachmentDao attachmentDao,
                                    MessageDao messageDao,
                                    ChatRepository chatRepository,
                                    LocalAssetStore assetStore, AppExecutors executors, IdGenerator idGenerator) {
        this.scriptRepository = scriptRepository; this.visualRepository = visualRepository; this.gateway = gateway;
        this.jobDao = jobDao; this.attachmentDao = attachmentDao; this.messageDao = messageDao;
        this.chatRepository = chatRepository;
        this.assetStore = assetStore;
        this.executors = executors; this.idGenerator = idGenerator;
    }

    public void enqueue(String scriptId, List<AiImageAction> actions, String requestId, long now) {
        if (actions == null || actions.isEmpty()) return;
        Script script = scriptRepository.getById(scriptId);
        if (script == null) return;
        // 旧剧本第一次收到图片动作时原地升级，保留原剧本 ID、聊天记录和文本设定。
        if (!script.isVisual()) {
            scriptRepository.setMediaMode(scriptId, Script.MediaMode.VISUAL, now);
        }
        for (AiImageAction action : actions) enqueueOne(scriptId, action, requestId, now);
    }

    private void enqueueOne(String scriptId, AiImageAction action, String requestId, long now) {
        // action 的 message_id 必须来自本批次事件，避免把图片错挂到旧消息上。
        if (action.getTrigger() == AiImageAction.Trigger.SPONTANEOUS_CHARACTER_SHARE
                && jobDao.countRecentSpontaneous(scriptId, action.getCharacterId(), now - 10 * 60 * 1000L) > 0) {
            return;
        }
        CharacterVisualProfile profile = visualRepository.getByCharacterId(action.getCharacterId());
        if (action.isIncludeCharacter() && (profile == null || !profile.isReady())) return;
        String jobId = idGenerator.newRequestId();
        ImageGenerationJobEntity job = new ImageGenerationJobEntity();
        job.id = jobId; job.script_id = scriptId; job.character_id = action.getCharacterId();
        job.message_id = action.getMessageId();
        if (job.message_id != null && messageDao.countById(job.message_id) == 0) {
            job.message_id = null;
        }
        if (job.message_id == null) {
            com.example.roleplaychat.data.local.entity.MessageEntity related =
                    messageDao.findLatestByRequestAndCharacter(requestId, action.getCharacterId());
            if (related != null) job.message_id = related.id;
        }
        if (job.message_id == null) {
            com.example.roleplaychat.domain.model.ChatMessage placeholder =
                    chatRepository.insertImagePlaceholder(scriptId, action.getCharacterId(), now);
            job.message_id = placeholder.getId();
        }
        job.client_job_id = "rp-" + jobId; job.trigger = action.getTrigger().name(); job.intent = action.getIntent().name();
        job.model = action.isIncludeCharacter() ? "qwenimage2.1" : "zimage";
        job.mode = action.isIncludeCharacter() ? "edit" : "txt2img";
        job.status = "CREATED"; job.prompt_snapshot = buildPrompt(action.isIncludeCharacter() ? profile : null, action); job.retry_count = 0; job.created_at = now;
        jobDao.insert(job);
        if (job.message_id != null) insertPendingAttachment(job, action);
        executors.networkIO().execute(() -> run(job, profile, action));
    }

    private void run(ImageGenerationJobEntity job, @Nullable CharacterVisualProfile profile, AiImageAction action) {
        try {
            job.status = "UPLOADING_REFERENCES"; job.started_at = System.currentTimeMillis(); jobDao.update(job);
            List<String> refs = !action.isIncludeCharacter() || profile == null ? Collections.emptyList() : profile.getAssets().isEmpty()
                    ? Collections.emptyList() : Collections.singletonList(profile.getAssets().get(0).getLocalPath());
            java.util.ArrayList<String> remote = new java.util.ArrayList<>();
            for (String ref : refs) {
                File file = assetStore.resolve(ref);
                if (file == null) throw new IllegalStateException("视觉身份文件不存在");
                remote.add(gateway.uploadAsset(file, sha256(file)));
            }
            ImageGenerationRequest.Model model = remote.isEmpty() ? ImageGenerationRequest.Model.ZIMAGE : ImageGenerationRequest.Model.QWEN_IMAGE_2_1;
            ImageGenerationRequest.Mode mode = remote.isEmpty() ? ImageGenerationRequest.Mode.TXT2IMG : ImageGenerationRequest.Mode.EDIT;
            ImageGenerationRequest request = new ImageGenerationRequest(job.client_job_id, job.script_id, job.character_id,
                    model, mode, job.prompt_snapshot, profile == null ? "" : safe(profile.getNegativePrompt()), refs,
                    768, 1024, Math.abs(System.nanoTime()), job.trigger);
            ImageGenerationStatus created = gateway.create(request, remote.toArray(new String[0]));
            job.huajing_job_id = created.getJobId(); job.status = "QUEUED"; jobDao.update(job);
            poll(job);
        } catch (Exception error) {
            job.status = "FAILED_RETRYABLE"; job.error_code = error.getMessage(); job.finished_at = System.currentTimeMillis(); jobDao.update(job);
            markAttachmentFailed(job);
        }
    }

    private void poll(ImageGenerationJobEntity job) throws Exception {
        for (int i = 0; i < 900; i++) {
            ImageGenerationStatus status = gateway.status(job.huajing_job_id);
            if (status == null) throw new IllegalStateException("Huajing 返回空状态");
            job.status = status.getState().name(); job.error_code = status.getErrorCode(); job.result_asset_id = status.getResultAssetId(); jobDao.update(job);
            updateProgressMessage(job, status);
            if (status.getState() == ImageGenerationStatus.State.READY && status.getResultAssetId() != null) {
                File dest = new File(assetStore.tmpDir(), "generated_" + job.id + ".png");
                gateway.download(status.getResultAssetId(), dest);
                String localRef;
                try (FileInputStream input = new FileInputStream(dest)) {
                    localRef = assetStore.storeStream(LocalAssetStore.DIR_GENERATED,
                            "generated_" + job.id, ".png", input);
                }
                dest.delete();
                job.status = "READY"; job.finished_at = System.currentTimeMillis(); jobDao.update(job);
                if (job.message_id != null) messageDao.updateContent(job.message_id, "");
                markAttachmentReady(job, localRef);
                return;
            }
            if (status.getState() == ImageGenerationStatus.State.FAILED_FINAL
                    || status.getState() == ImageGenerationStatus.State.FAILED_RETRYABLE
                    || status.getState() == ImageGenerationStatus.State.CANCELLED
                    || status.getState() == ImageGenerationStatus.State.EXPIRED) {
                job.finished_at = System.currentTimeMillis();
                job.error_code = job.error_code == null ? status.getState().name() : job.error_code;
                jobDao.update(job);
                if (job.message_id != null) messageDao.updateContent(job.message_id, "图片生成失败：" + job.error_code);
                markAttachmentFailed(job);
                return;
            }
            Thread.sleep(1200L);
        }
        job.status = "FAILED_RETRYABLE"; job.error_code = "GENERATION_TIMEOUT"; job.finished_at = System.currentTimeMillis(); jobDao.update(job);
        markAttachmentFailed(job);
        if (job.message_id != null) messageDao.updateContent(job.message_id, "图片生成超时，可稍后重试");
    }

    private void updateProgressMessage(ImageGenerationJobEntity job, ImageGenerationStatus status) {
        if (job.message_id == null) return;
        long elapsed = Math.max(0L, System.currentTimeMillis() - (job.started_at == null ? job.created_at : job.started_at));
        long seconds = elapsed / 1000L;
        String stage = status.getStage() == null || status.getStage().trim().isEmpty()
                ? status.getState().name() : status.getStage();
        int percent = Math.round(status.getProgress() * 100f);
        String progress = percent > 0 ? "（" + Math.max(0, Math.min(100, percent)) + "%）" : "";
        messageDao.updateContent(job.message_id, "正在生成图片：" + stage + progress + " · 已用时 " + seconds + " 秒");
    }

    private static String buildPrompt(@Nullable CharacterVisualProfile profile, AiImageAction action) {
        StringBuilder prompt = new StringBuilder();
        if (profile != null) {
            if (profile.getIdentityPrompt() != null) prompt.append(profile.getIdentityPrompt()).append('\n');
            if (profile.getAppearanceJson() != null) prompt.append("保持以下人物外貌和身高体型设定不变：").append(profile.getAppearanceJson()).append('\n');
            prompt.append("保持身份参考图中的人脸、年龄、发型、肤色和明显特征不变。\n");
        }
        prompt.append("场景：").append(action.getScene());
        if (action.getFraming() != null) prompt.append("\n构图：").append(action.getFraming());
        if (action.getMood() != null) prompt.append("\n氛围：").append(action.getMood());
        if (action.getOutfit() != null) prompt.append("\n服装：").append(action.getOutfit());
        return prompt.toString();
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private void insertPendingAttachment(ImageGenerationJobEntity job, AiImageAction action) {
        MessageAttachmentEntity attachment = new MessageAttachmentEntity();
        attachment.id = idGenerator.newRequestId(); attachment.message_id = job.message_id;
        attachment.character_id = action.getCharacterId(); attachment.type = "IMAGE";
        attachment.status = "PENDING"; attachment.mime_type = "image/png";
        attachment.width = 768; attachment.height = 1024; attachment.job_id = job.id;
        attachment.sort_index = 0; attachment.created_at = job.created_at;
        attachmentDao.insert(attachment);
    }

    private void markAttachmentReady(ImageGenerationJobEntity job, String localRef) {
        if (localRef == null || job.message_id == null) return;
        MessageAttachmentEntity attachment = attachmentDao.getByJobId(job.id);
        if (attachment == null) return;
        attachment.status = "READY"; attachment.local_path = localRef; attachmentDao.update(attachment);
        messageDao.touch(job.message_id);
    }

    private void markAttachmentFailed(ImageGenerationJobEntity job) {
        if (job.message_id == null) return;
        MessageAttachmentEntity attachment = attachmentDao.getByJobId(job.id);
        if (attachment == null) return;
        attachment.status = "FAILED"; attachmentDao.update(attachment);
        messageDao.touch(job.message_id);
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = Files.readAllBytes(file.toPath());
        byte[] hash = digest.digest(bytes);
        StringBuilder out = new StringBuilder(hash.length * 2);
        for (byte b : hash) out.append(String.format("%02x", b));
        return out.toString();
    }
}
