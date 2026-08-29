package com.example.roleplaychat.data.mapper;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * TavernAI 类角色卡 JSON 数据模型（架构文档 §9.3 映射）。
 * 按测试样本声明支持版本，不笼统承诺所有变体。
 */
public final class TavernCharacterMapper {

    public static final String SUPPORTED_FORMAT_VERSION = "TavernAI v2 (card v2) 子集";

    private TavernCharacterMapper() {
    }

    public static final class TavernCard {
        @SerializedName("spec")
        public String spec = "chara_card_v2";
        @SerializedName("spec_version")
        public String specVersion = "2.0";
        @SerializedName("data")
        public TavernData data;
    }

    public static final class TavernData {
        @SerializedName("name")
        public String name;
        @SerializedName("description")
        public String description;
        @SerializedName("personality")
        public String personality;
        @SerializedName("first_mes")
        public String firstMes;
        @SerializedName("mes_example")
        public String mesExample;
        @SerializedName("scenario")
        public String scenario;
        @SerializedName("system_prompt")
        public String systemPrompt;
        @SerializedName("char_greeting")
        public String charGreeting;
        @SerializedName("avatar")
        public String avatar; // base64 PNG/WebP 或路径
        @SerializedName("extensions")
        public java.util.Map<String, Object> extensions = new java.util.LinkedHashMap<>();
    }

    /** 旧式单层结构（部分工具导出无 spec 包装）。 */
    public static final class FlatCard {
        @SerializedName("name")
        public String name;
        @SerializedName("description")
        public String description;
        @SerializedName("personality")
        public String personality;
        @SerializedName("first_mes")
        public String firstMes;
        @SerializedName("mes_example")
        public String mesExample;
        @SerializedName("scenario")
        public String scenario;
        @SerializedName("system_prompt")
        public String systemPrompt;
        @SerializedName("char_greeting")
        public String charGreeting;
        @SerializedName("avatar")
        public String avatar;
    }

    /** 由 Tavern 数据构建自定义格式 data（架构文档 §9.3 字段映射）。 */
    public static CharacterCardMapper.Data toCustomData(TavernData data) {
        CharacterCardMapper.Data custom = new CharacterCardMapper.Data();
        custom.name = data.name;
        custom.personality = data.personality;
        custom.backstory = firstNonEmpty(data.description, data.scenario);
        custom.systemPrompt = data.systemPrompt;

        List<String> samples = new ArrayList<>();
        String greeting = firstNonEmpty(data.firstMes, data.charGreeting);
        if (greeting != null && !greeting.isEmpty()) {
            samples.add(greeting);
        }
        if (data.mesExample != null && !data.mesExample.isEmpty()) {
            // 按明确分隔符解析，否则保存单条（架构文档 §9.3）
            String separator = detectSeparator(data.mesExample);
            if (separator != null) {
                String[] parts = data.mesExample.split(separator);
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        samples.add(trimmed);
                    }
                }
            } else {
                samples.add(data.mesExample.trim());
            }
        }
        custom.sampleLines = samples;
        return custom;
    }

    public static CharacterCardMapper.Data toCustomData(FlatCard card) {
        CharacterCardMapper.Data custom = new CharacterCardMapper.Data();
        custom.name = card.name;
        custom.personality = card.personality;
        custom.backstory = firstNonEmpty(card.description, card.scenario);
        custom.systemPrompt = card.systemPrompt;
        List<String> samples = new ArrayList<>();
        String greeting = firstNonEmpty(card.firstMes, card.charGreeting);
        if (greeting != null && !greeting.isEmpty()) {
            samples.add(greeting);
        }
        if (card.mesExample != null && !card.mesExample.isEmpty()) {
            samples.add(card.mesExample.trim());
        }
        custom.sampleLines = samples;
        return custom;
    }

    @Nullable
    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    @Nullable
    private static String detectSeparator(String mesExample) {
        for (String sep : new String[]{"<START>", "<END>", "***", "\n\n---\n\n"}) {
            if (mesExample.contains(sep)) {
                return sep;
            }
        }
        return null;
    }
}
