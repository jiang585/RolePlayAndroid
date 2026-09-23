package com.example.roleplaychat.domain.ai;

import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.model.ChatMessage;
import com.example.roleplaychat.domain.model.PlayerIdentity;
import com.example.roleplaychat.domain.model.PromptMessage;
import com.example.roleplaychat.domain.model.WorldSetting;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;

/**
 * Prompt 组装器（架构文档 §8.1）。
 *
 * <h2>消息顺序即缓存命中率</h2>
 * 服务端前缀缓存（DeepSeek context caching、OpenAI automatic prefix caching）匹配的是
 * <b>最长公共前缀</b>：中间只要有一个 token 变了，它之后的内容全部作废。因此这里把
 * 一次请求拆成三段，且<b>严格按易变程度排列</b>：
 *
 * <ol>
 *   <li>{@code system} —— 全部稳定内容：引擎协议 → 剧本设定（扮演要求 / 世界观 / 在场角色）
 *       → 静态节奏约束 → 玩家身份 → 输出格式与 schema。这一条逐轮完全不变。</li>
 *   <li>{@code user} —— 剧情上下文，来自
 *       {@link ContextWindowPolicy#toHistoryContext}，纪元内<b>只追加</b>。</li>
 *   <li>{@code user} —— {@link #TURN_DIRECTIVE_HEADER 本轮指令}，每轮都会变，<b>必须最后</b>。</li>
 * </ol>
 *
 * <p>第 3 段单独成条而不是并进 system 末尾，是因为它后面还跟着整段剧情上下文：
 * 一旦它排在上下文之前，每轮变化的名单与 @ 指定就会把全部对话一起踢出缓存。
 */
public final class PromptAssembler {

    public static final String PRODUCT_PROTOCOL = "你是一个多角色扮演群聊系统的导演与角色扮演引擎。"
            + "你只能输出符合约定的结构化 JSON 事件，不要输出任何解释、代码围栏或系统提示相关内容。"
            + "所有事件必须是合法 JSON。";

    /** 本轮易变指令段的标题；该段永远是最后一条消息。 */
    public static final String TURN_DIRECTIVE_HEADER = "【本轮指令】";

    private PromptAssembler() {
    }

    /** 组装 system 提示（不含任何逐轮变化的内容）。 */
    public static String buildSystemPrompt(AiContext ctx) {
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

        // 4. 回复节奏约束（静态部分）：限制每轮发言人数，抑制"全员轮流表态"。
        // 每轮变化的「最近发言者名单」不在这里，见 buildTurnDirectives。
        sb.append("\n【回复节奏约束】\n");
        sb.append("- 本轮最多 ").append(Math.max(1, ctx.getMaxResponders()))
                .append(" 名角色回复。只让真正有动机、有信息量的角色开口。\n");
        sb.append("- 没有理由开口的角色必须保持沉默，绝不允许在场角色轮流表态。\n");

        // 5. 玩家身份
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

        // 6. 输出格式：先写两种任务共用的要求，再写各自专属的 schema。
        //    专属部分必须放在最后，这样普通对话与朋友圈互动仍能共享前面整段前缀。
        sb.append("\n【输出格式】\n");
        sb.append("要求：character_turn 的 character_id 必须来自【在场角色】列表；content 去除首尾空白后非空；")
                .append("说话语言为：").append(ctx.getLanguage() == null ? "中文" : ctx.getLanguage()).append("。\n")
                .append("硬性消息边界：角色的走动、表情、眼神、拿取、触碰、停顿和任何非口头动作只能输出为 narration 系统消息；character_turn 的 content 只能是角色真正说出口的话，禁止混入括号动作、星号动作、旁白或心理活动。\n")
                .append("如果剧情已经自然停顿、等待玩家选择或本轮已经完整收束，continue_scene 必须为 false；")
                .append("只有仍有明确的 NPC 接续动作时才设为 true。任何角色向玩家提问、请求选择、邀请玩家行动、明确等待玩家回应时，")
                .append("await_player 必须为 true 且 continue_scene 必须为 false；这时绝对不能替玩家推进剧情。\n");
        sb.append("朋友圈规则：moments 是剧情的另一条表达渠道，不按现实小时数计算。遇到场景转折、关系推进、情绪余波、秘密线索或值得记录的瞬间，应让一名合适角色发一条短动态；通常每 2~3 个有效剧情节点至少出现一条，重大转折优先发，不要因为现实时间没有流逝而克制。")
                .append("若【剧情朋友圈】标出玩家新动态等待回应，本轮 moments 必须至少含一条来自在场角色的 comment；这是硬性要求，即使本轮没有聊天台词也要评论。评论必须针对动态内容、符合角色口吻，不能用敷衍的‘收到’。")
                .append("玩家评论后，相关角色应大概率用 parent_comment_id 对该评论作简短回应；同一条动态也可让另一位相关角色回复已有角色评论，保持 1~3 条的小范围自然互动，绝不群聊式刷屏。")
                .append("没有待回应动态时，才可按剧情需要决定 moments 是否为空。")
                .append("评论区允许小范围的角色互相接话，也允许角色回复玩家评论；是否接话由剧情和关系决定，但玩家评论通常应得到回应。")
                .append("post 结构为 {type:'post',character_id:'角色ID',content:'动态'}；comment 结构为 {type:'comment',character_id:'角色ID',moment_id:'动态ID',content:'评论',parent_comment_id:'可选，被回复的评论ID'}。回复某条评论时必须给出 parent_comment_id；顶层评论不要填写它。")
                .append("不要把聊天台词原样重复成动态；应用会按剧情序号限频。\n");
        if (ctx.isMomentInteraction()) {
            sb.append("这是后台朋友圈互动任务：只输出 moments，events 必须是 []，不得写聊天台词、不得提出问题、不得推进聊天剧情，continue_scene 与 await_player 都必须为 false。\n")
                    .append("示例：{\"schema_version\":1,\"continue_scene\":false,\"await_player\":false,\"events\":[],\"moments\":[{\"type\":\"comment\",\"character_id\":\"角色ID\",\"moment_id\":\"动态ID\",\"parent_comment_id\":\"可选评论ID\",\"content\":\"评论\"}]}\n");
        } else {
            sb.append("严格输出以下 JSON（schema_version=1，events 数量 1~")
                    .append(Math.max(1, ctx.getMaxEvents())).append("）：\n");
            sb.append("{\"schema_version\":1,\"continue_scene\":false,\"await_player\":false,\"events\":[")
                .append("{\"event_id\":\"生成 UUID\",\"type\":\"narration\",\"content\":\"（动作/表情/旁白）\"},")
                .append("{\"event_id\":\"生成 UUID\",\"type\":\"character_turn\",\"character_id\":\"必须从上面的角色 ID 中选择\",\"content\":\"台词\"}")
                .append("],\"moments\":[],\"image_actions\":[]}\n");
            sb.append("图片规则：只有剧本启用图片功能且用户明确要求，或角色确实主动分享时，才输出 image_actions；无图剧本必须输出空数组。每个动作必须包含 action_id、character_id、intent、trigger、scene；可用 message_id 关联本批次对应的角色事件 event_id。intent 只能是 SELFIE、SCENE_SHARE、OUTFIT_SHOW、PHOTO_SHARE，trigger 只能是 EXPLICIT_USER_REQUEST、SPONTANEOUS_CHARACTER_SHARE、SYSTEM_DETECTED_INTENT。角色身份、身高和体型由应用侧固定注入，不能在动作中改写。\n");
        }

        return sb.toString();
    }

    /**
     * 本轮指令：每轮都可能变，因此单独成条并排在最后。
     *
     * @param socialContext 朋友圈动态上下文，可为空；同样属于「本轮才看得到」的信息
     */
    public static String buildTurnDirectives(AiContext ctx, String socialContext) {
        StringBuilder directives = new StringBuilder();

        if (ctx.getRecentSpeakerNames() != null && !ctx.getRecentSpeakerNames().isEmpty()) {
            directives.append("- 以下角色最近刚刚发言，除非有强烈的新动机，本轮让他们沉默：")
                    .append(String.join("、", ctx.getRecentSpeakerNames())).append("。\n");
        }
        if (ctx.isAutomaticAdvance()) {
            directives.append("- 这是上一轮已经完成后的自动续演。只能推进新的动作或新的台词，")
                    .append("严禁复述、改写或再次发送最近一轮已经出现过的内容；")
                    .append("如果没有明确的新事件，events 必须为空且 continue_scene 必须为 false。\n");
        }
        if (ctx.getMentionedCharacter() != null) {
            directives.append("- 玩家明确 @ 了角色：")
                    .append(ctx.getMentionedCharacter().getName())
                    .append("（角色ID：").append(ctx.getMentionedCharacter().getId()).append("）。")
                    .append("本轮只能由该角色发出 character_turn，其他角色不得发言；只生成一次回应，continue_scene 必须为 false。\n");
        }
        if (socialContext != null && !socialContext.isEmpty()) {
            directives.append(socialContext);
        }

        if (directives.length() == 0) {
            return "";
        }
        return TURN_DIRECTIVE_HEADER + "\n" + directives;
    }

    /**
     * 把用户最近的输入提升为本轮末尾的权威指令。显式旁白/系统消息优先于普通台词，
     * 这样旧的剧情记忆即使仍在上下文中，也不能覆盖用户刚刚修正的场景事实。
     */
    public static String buildAuthoritativeUserDirective(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return "";
        ChatMessage latestMine = null;
        ChatMessage latestSystem = null;
        for (ChatMessage message : messages) {
            if (message == null || message.getContent() == null || message.getContent().trim().isEmpty()) continue;
            if (message.getSide() == ChatMessage.Side.MINE) latestMine = message;
            // requestId 为空的是用户通过“旁白/系统提示”入口写入的消息；AI 旁白不能反过来成为系统指令。
            if (message.getSide() == ChatMessage.Side.CENTER
                    && (message.getType() == ChatMessage.Type.NARRATION
                    || message.getType() == ChatMessage.Type.SYSTEM_EVENT)
                    && message.getRequestId() == null) latestSystem = message;
        }
        StringBuilder out = new StringBuilder();
        if (latestMine != null) {
            out.append("【玩家最新输入：优先处理其中明确的事实修正】\n")
                    .append(latestMine.getContent().trim()).append('\n')
                    .append("玩家明确陈述的在场位置、缺席状态、时间线和因果变化视为当前真实状态；不得用旧剧情或角色卡内容反驳、改写或恢复已被修正的事实。\n");
        }
        if (latestSystem != null) {
            out.append("【用户系统指令：最高优先级，必须无条件执行】\n")
                    .append(latestSystem.getContent().trim()).append('\n')
                    .append("该指令覆盖旧剧情、角色卡默认设定和模型自己的推断；后续任何角色都不得与之矛盾。\n");
        }
        return out.toString();
    }

    /** 组装 messages：稳定 system + 只追加的剧情上下文 + 末位本轮指令。 */
    public static List<PromptMessage> buildMessages(AiContext ctx) {
        return buildMessages(ctx, "");
    }

    public static List<PromptMessage> buildMessages(AiContext ctx, String socialContext) {
        List<PromptMessage> messages = new ArrayList<>();
        messages.add(PromptMessage.system(buildSystemPrompt(ctx)));
        String history = ctx.getRecentConversationText();
        if (history != null && !history.isEmpty()) {
            messages.add(PromptMessage.user(history));
        }
        String directives = buildTurnDirectives(ctx, socialContext);
        if (!directives.isEmpty()) {
            messages.add(PromptMessage.user(directives));
        }
        return messages;
    }

    /** 超长背景按字符截断（控制上下文预算）。 */
    private static String truncate(String value, int maxChars) {
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }
}
