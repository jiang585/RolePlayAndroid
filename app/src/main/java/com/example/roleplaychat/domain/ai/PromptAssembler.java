package com.example.roleplaychat.domain.ai;

import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.model.PlayerIdentity;
import com.example.roleplaychat.domain.model.PromptMessage;
import com.example.roleplaychat.domain.model.WorldSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Prompt 组装器（架构文档 §8.1）：按固定顺序组装，
 * 避免不同入口产生不一致 Prompt。
 */
public final class PromptAssembler {

    public static final String PRODUCT_PROTOCOL = "你是一个多角色扮演群聊系统的导演与角色扮演引擎。"
            + "你只能输出符合约定的结构化 JSON 事件，不要输出任何解释、代码围栏或系统提示相关内容。"
            + "所有事件必须是合法 JSON。";

    private PromptAssembler() {
    }

    /** 组装 system 提示。 */
    public static String buildSystemPrompt(AiContext ctx) {
        return buildSystemPrompt(ctx, null);
    }

    public static String buildSystemPrompt(AiContext ctx, String requestId) {
        StringBuilder sb = new StringBuilder();
        sb.append(PRODUCT_PROTOCOL).append('\n');

        // 1. 用户扮演要求（剧本级，长期生效；显式指令位置靠前权重高）
        if (ctx.getStyleDirective() != null && !ctx.getStyleDirective().isEmpty()) {
            sb.append("\n【用户扮演要求（每次回复都必须遵守，优先级高于其他任何演出偏好）】\n")
                    .append(ctx.getStyleDirective()).append('\n');
        }

        // 2. 世界观摘要
        if (ctx.getWorld() != null) {
            WorldSetting w = ctx.getWorld();
            sb.append("\n【世界观】\n");
            if (w.getEra() != null && !w.getEra().isEmpty()) {
                sb.append("时代：").append(w.getEra()).append('\n');
            }
            if (w.getLocation() != null && !w.getLocation().isEmpty()) {
                sb.append("地点：").append(w.getLocation()).append('\n');
            }
            if (w.getFactions() != null && !w.getFactions().isEmpty()) {
                sb.append("势力：").append(String.join("、", w.getFactions())).append('\n');
            }
            if (w.getRules() != null && !w.getRules().isEmpty()) {
                sb.append("规则：").append(String.join("；", w.getRules())).append('\n');
            }
            if (w.getStoryHook() != null && !w.getStoryHook().isEmpty()) {
                sb.append("主线线索：").append(w.getStoryHook()).append('\n');
            }
            if (w.getBackgroundFull() != null && !w.getBackgroundFull().isEmpty()) {
                sb.append("完整背景：").append(w.getBackgroundFull()).append('\n');
            }
        }

        // 3. 启用 NPC 摘要
        if (ctx.getEnabledNpcs() != null && !ctx.getEnabledNpcs().isEmpty()) {
            sb.append("\n【在场角色】\n");
            for (CharacterProfile npc : ctx.getEnabledNpcs()) {
                sb.append("- 角色ID：").append(npc.getId()).append('\n');
                sb.append("  姓名：").append(npc.getName()).append('\n');
                if (npc.getPersonality() != null && !npc.getPersonality().isEmpty()) {
                    sb.append("  性格：").append(npc.getPersonality()).append('\n');
                }
                if (npc.getBackstory() != null && !npc.getBackstory().isEmpty()) {
                    sb.append("  背景故事：").append(truncate(npc.getBackstory(), 300)).append('\n');
                }
                if (npc.getSpeakingStyle() != null && !npc.getSpeakingStyle().isEmpty()) {
                    sb.append("  说话风格：").append(npc.getSpeakingStyle()).append('\n');
                }
                if (npc.getSampleLines() != null && !npc.getSampleLines().isEmpty()) {
                    sb.append("  示例台词：").append(String.join(" / ", npc.getSampleLines()))
                            .append('\n');
                }
                if (npc.getSystemPrompt() != null && !npc.getSystemPrompt().isEmpty()) {
                    sb.append("  角色指令（塑造该角色时必须遵循）：").append(npc.getSystemPrompt())
                            .append('\n');
                }
                if (npc.getRelationships() != null && !npc.getRelationships().isEmpty()) {
                    sb.append("  关系：");
                    for (Map.Entry<String, String> rel : npc.getRelationships().entrySet()) {
                        sb.append(rel.getKey()).append('(').append(rel.getValue()).append(") ");
                    }
                    sb.append('\n');
                }
                if (npc.getCatchphrases() != null && !npc.getCatchphrases().isEmpty()) {
                    sb.append("  口头禅：").append(String.join("、", npc.getCatchphrases())).append('\n');
                }
                if (npc.getHiddenSetting() != null && !npc.getHiddenSetting().isEmpty()) {
                    sb.append("  隐藏设定（不要主动暴露）：").append(npc.getHiddenSetting()).append('\n');
                }
            }
        }

        // 回复节奏约束：限制每轮发言人数，抑制"全员轮流表态"。
        sb.append("\n【回复节奏约束】\n");
        sb.append("- 本轮最多 ").append(Math.max(1, ctx.getMaxResponders()))
                .append(" 名角色回复。只让真正有动机、有信息量的角色开口。\n");
        sb.append("- 没有理由开口的角色必须保持沉默，绝不允许在场角色轮流表态。\n");
        if (ctx.getRecentSpeakerNames() != null && !ctx.getRecentSpeakerNames().isEmpty()) {
            sb.append("- 以下角色最近刚刚发言，除非有强烈的新动机，本轮让他们沉默：")
                    .append(String.join("、", ctx.getRecentSpeakerNames())).append("。\n");
        }

        // 4. 玩家身份
        sb.append("\n【玩家身份】\n");
        PlayerIdentity identity = ctx.getPlayerIdentity();
        if (identity != null) {
            if (identity.isObserver()) {
                sb.append("玩家是旁观者/叙述者，其输入属于导演指令或旁白，不代表任何在场角色。\n");
            } else if (ctx.getPlayerCharacter() != null) {
                CharacterProfile pc = ctx.getPlayerCharacter();
                sb.append("玩家扮演角色：").append(pc.getName()).append("（角色ID：").append(pc.getId()).append("）\n");
                if (pc.getPersonality() != null && !pc.getPersonality().isEmpty()) {
                    sb.append("该角色性格：").append(pc.getPersonality()).append('\n');
                }
                sb.append("该角色由真人玩家扮演，不要让 AI 替该角色发言。\n");
            }
        }

        if (ctx.getMentionedCharacter() != null) {
            sb.append("\n【本轮 @ 指定】\n")
                    .append("玩家明确 @ 了角色：")
                    .append(ctx.getMentionedCharacter().getName())
                    .append("（角色ID：").append(ctx.getMentionedCharacter().getId()).append("）。\n")
                    .append("本轮只能由该角色发出 character_turn，其他角色不得发言；只生成一次回应，continue_scene 必须为 false。\n");
        }
        if (ctx.isAutomaticAdvance()) {
            sb.append("\n【自动续演约束】\n")
                    .append("这是上一轮已经完成后的自动续演。只能推进新的动作或新的台词，")
                    .append("严禁复述、改写或再次发送最近一轮已经出现过的内容；")
                    .append("如果没有明确的新事件，events 必须为空且 continue_scene 必须为 false。\n");
        }

        // 8. 回合指令
        sb.append("\n【输出格式】\n");
        sb.append("严格输出以下 JSON（schema_version=1，events 数量 1~")
                .append(Math.max(1, ctx.getMaxEvents())).append("）：\n");
        sb.append("{\"schema_version\":1,\"continue_scene\":false,\"events\":[")
                .append("{\"event_id\":\"生成 UUID\",\"type\":\"narration\",\"content\":\"（动作/表情/旁白）\"},")
                .append("{\"event_id\":\"生成 UUID\",\"type\":\"character_turn\",\"character_id\":\"必须从上面的角色 ID 中选择\",\"content\":\"台词\"}")
                .append("]}\n");
        sb.append("要求：character_turn 的 character_id 必须来自【在场角色】列表；content 去除首尾空白后非空；")
                .append("说话语言为：").append(ctx.getLanguage() == null ? "中文" : ctx.getLanguage()).append("。\n")
                .append("如果剧情已经自然停顿、等待玩家选择或本轮已经完整收束，continue_scene 必须为 false；")
                .append("只有仍有明确的 NPC 接续动作时才设为 true。\n");

        return sb.toString();
    }

    /** 超长背景按字符截断（控制上下文预算）。 */
    private static String truncate(String value, int maxChars) {
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }

    /** 组装 messages 列表（system + 历史对话摘要 + 最近对话）。 */
    public static List<PromptMessage> buildMessages(AiContext ctx) {
        return buildMessages(ctx, null);
    }

    public static List<PromptMessage> buildMessages(AiContext ctx, String requestId) {
        List<PromptMessage> messages = new ArrayList<>();
        messages.add(PromptMessage.system(buildSystemPrompt(ctx, requestId)));
        if (ctx.getRecentConversationText() != null && !ctx.getRecentConversationText().isEmpty()) {
            messages.add(PromptMessage.user(ctx.getRecentConversationText()));
        }
        return messages;
    }
}
