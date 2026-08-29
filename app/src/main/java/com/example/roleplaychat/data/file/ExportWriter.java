package com.example.roleplaychat.data.file;

import androidx.annotation.Nullable;

import com.example.roleplaychat.data.mapper.CharacterCardMapper;
import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.AppErrorCode;
import com.example.roleplaychat.domain.model.ChatMessage;
import com.example.roleplaychat.domain.model.WorldSetting;
import com.example.roleplaychat.util.JsonUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;

/**
 * 导出写入器（架构文档 §9）：统一 UTF-8、snake_case 信封与文件命名。
 */
public final class ExportWriter {

    private static final String GENERATOR = "RolePlayChat/1.0";

    private ExportWriter() {
    }

    /** 写出角色卡信封。 */
    @Nullable
    public static AppError writeCharacterCard(File target, CharacterCardMapper.Envelope envelope) {
        if (envelope.exportedAt == null) {
            envelope.exportedAt = isoNow();
        }
        return writeJson(target, envelope);
    }

    /** 写出世界观信封。 */
    @Nullable
    public static AppError writeWorld(File target, WorldSetting world) {
        Envelope envelope = new Envelope();
        envelope.format = "roleplay-world";
        envelope.schemaVersion = 1;
        envelope.generator = GENERATOR;
        envelope.data = WorldDto.from(world);
        return writeJson(target, envelope);
    }

    /** 写出聊天 JSON（架构文档 §9.4）。 */
    @Nullable
    public static AppError writeChatJson(File target, List<ChatMessage> messages) {
        ChatEnvelope envelope = new ChatEnvelope();
        envelope.format = "roleplay-chat";
        envelope.schemaVersion = 1;
        envelope.generator = GENERATOR;
        for (ChatMessage m : messages) {
            if (m.getStatus() == ChatMessage.Status.FAILED && m.getContent().isEmpty()) {
                continue; // 默认排除失败空消息
            }
            ChatItem item = new ChatItem();
            item.id = m.getId();
            item.sequence = m.getSequence();
            item.createdAt = m.getCreatedAt();
            item.type = m.getType().name();
            item.senderName = m.getSenderDisplayName();
            item.content = m.getContent();
            item.status = m.getStatus().name();
            envelope.messages.add(item);
        }
        return writeJson(target, envelope);
    }

    /** 写出聊天 TXT（架构文档 §9.4 可读格式）。 */
    @Nullable
    public static AppError writeChatTxt(File target, List<ChatMessage> messages) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : messages) {
            if (m.getStatus() == ChatMessage.Status.FAILED && m.getContent().isEmpty()) {
                continue;
            }
            String time = fmt.format(new Date(m.getCreatedAt()));
            String who;
            if (m.getType() == ChatMessage.Type.NARRATION) {
                who = "旁白";
            } else if (m.getSide() == ChatMessage.Side.MINE) {
                who = "我" + (m.getSenderDisplayName() == null ? "" : " / " + m.getSenderDisplayName());
            } else {
                who = m.getSenderDisplayName() == null ? "未知" : m.getSenderDisplayName();
            }
            sb.append('[').append(time).append("] ").append(who).append("：")
                    .append(m.getContent()).append('\n');
        }
        try {
            Files.write(target.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
            return null;
        } catch (IOException e) {
            return AppError.of(AppErrorCode.UNKNOWN, e.getMessage(), false);
        }
    }

    /** 写出可打印的聊天 PDF，按页自动换行。 */
    @Nullable
    public static AppError writeChatPdf(File target, List<ChatMessage> messages) {
        PdfDocument document = new PdfDocument();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(11f);
        int pageNumber = 1;
        PdfDocument.Page page = null;
        Canvas canvas = null;
        float y = 40f;
        try {
            for (ChatMessage message : messages) {
                if (message.getStatus() == ChatMessage.Status.FAILED && message.getContent().isEmpty()) continue;
                String who = message.getType() == ChatMessage.Type.NARRATION ? "旁白"
                        : (message.getSide() == ChatMessage.Side.MINE ? "我" :
                        (message.getSenderDisplayName() == null ? "未知" : message.getSenderDisplayName()));
                String prefix = who + "：";
                String text = prefix + message.getContent().replace("\r", "").replace("\n", " ");
                int start = 0;
                while (start < text.length()) {
                    if (page == null || y > 790f) {
                        if (page != null) document.finishPage(page);
                        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(595, 842, pageNumber++).create();
                        page = document.startPage(info); canvas = page.getCanvas(); y = 40f;
                    }
                    int end = Math.min(text.length(), start + 75);
                    canvas.drawText(text.substring(start, end), 36f, y, paint);
                    y += 18f; start = end;
                }
                y += 6f;
            }
            if (page != null) document.finishPage(page);
            document.writeTo(new java.io.FileOutputStream(target));
            return null;
        } catch (IOException e) {
            return AppError.of(AppErrorCode.UNKNOWN, e.getMessage(), false);
        } finally {
            document.close();
        }
    }

    @Nullable
    private static AppError writeJson(File target, Object envelope) {
        String json = JsonUtils.toJson(envelope);
        try {
            Files.write(target.toPath(), json.getBytes(StandardCharsets.UTF_8));
            return null;
        } catch (IOException e) {
            return AppError.of(AppErrorCode.UNKNOWN, e.getMessage(), false);
        }
    }

    private static String isoNow() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
                .format(new Date());
    }

    /** 通用信封（架构文档 §9.1）。 */
    public static final class Envelope {
        @com.google.gson.annotations.SerializedName("format")
        public String format;
        @com.google.gson.annotations.SerializedName("schema_version")
        public int schemaVersion;
        @com.google.gson.annotations.SerializedName("exported_at")
        public String exportedAt;
        @com.google.gson.annotations.SerializedName("generator")
        public String generator;
        @com.google.gson.annotations.SerializedName("data")
        public Object data;
    }

    /** 聊天信封。 */
    public static final class ChatEnvelope {
        @com.google.gson.annotations.SerializedName("format")
        public String format;
        @com.google.gson.annotations.SerializedName("schema_version")
        public int schemaVersion;
        @com.google.gson.annotations.SerializedName("exported_at")
        public String exportedAt;
        @com.google.gson.annotations.SerializedName("generator")
        public String generator;
        @com.google.gson.annotations.SerializedName("messages")
        public java.util.List<ChatItem> messages = new java.util.ArrayList<>();
    }

    public static final class ChatItem {
        @com.google.gson.annotations.SerializedName("id")
        public String id;
        @com.google.gson.annotations.SerializedName("sequence")
        public long sequence;
        @com.google.gson.annotations.SerializedName("created_at")
        public long createdAt;
        @com.google.gson.annotations.SerializedName("type")
        public String type;
        @com.google.gson.annotations.SerializedName("sender_name")
        public String senderName;
        @com.google.gson.annotations.SerializedName("content")
        public String content;
        @com.google.gson.annotations.SerializedName("status")
        public String status;
    }

    /** 世界观导出 DTO（snake_case，架构文档 §9.1/§14.2）。 */
    public static final class WorldDto {
        @com.google.gson.annotations.SerializedName("external_id")
        public String externalId;
        @com.google.gson.annotations.SerializedName("era")
        public String era;
        @com.google.gson.annotations.SerializedName("location")
        public String location;
        @com.google.gson.annotations.SerializedName("factions")
        public java.util.List<String> factions = new java.util.ArrayList<>();
        @com.google.gson.annotations.SerializedName("rules")
        public java.util.List<String> rules = new java.util.ArrayList<>();
        @com.google.gson.annotations.SerializedName("story_hook")
        public String storyHook;
        @com.google.gson.annotations.SerializedName("background_full")
        public String backgroundFull;
        @com.google.gson.annotations.SerializedName("tags")
        public java.util.List<String> tags = new java.util.ArrayList<>();
        @com.google.gson.annotations.SerializedName("version_note")
        public String versionNote;
        @com.google.gson.annotations.SerializedName("updated_at")
        public long updatedAt;

        static WorldDto from(WorldSetting w) {
            WorldDto dto = new WorldDto();
            dto.externalId = w.getId();
            dto.era = w.getEra();
            dto.location = w.getLocation();
            dto.factions = new java.util.ArrayList<>(w.getFactions());
            dto.rules = new java.util.ArrayList<>(w.getRules());
            dto.storyHook = w.getStoryHook();
            dto.backgroundFull = w.getBackgroundFull();
            dto.tags = new java.util.ArrayList<>(w.getTags());
            dto.versionNote = w.getVersionNote();
            dto.updatedAt = w.getUpdatedAt();
            return dto;
        }
    }
}
