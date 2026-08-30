package com.example.roleplaychat.domain.ai;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;

import com.example.roleplaychat.domain.ai.AiContext;
import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.model.PlayerIdentity;
import com.example.roleplaychat.domain.model.PromptMessage;
import com.example.roleplaychat.domain.model.ChatMessage;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Prompt 组装器测试（架构文档 §15.2-2：玩家绑定角色后 NPC 列表排除该角色）。
 */
public class PromptAssemblerTest {

    @Test
    public void buildMessages_containsSystemAndUser() {
        AiContext context = new AiContext("script-1", null, new ArrayList<>(),
                null, null, "最近对话", "中文", 8);
        List<PromptMessage> messages = PromptAssembler.buildMessages(context);
        assertTrue(messages.size() >= 1);
        assertNotNull(messages.get(0).getContent());
    }

    @Test
    public void systemPrompt_includesPlayerIdentityExclusion() {
        CharacterProfile player = profile("char-player", "林晚晴");
        CharacterProfile npc = profile("char-npc", "张三");
        List<CharacterProfile> npcs = new ArrayList<>(Arrays.asList(player, npc));
        PlayerIdentity identity = new PlayerIdentity("script-1",
                PlayerIdentity.RoleType.PROTAGONIST, "char-player", 0L);
        AiContext context = new AiContext("script-1", null, npcs, identity, player,
                "你好", "中文", 8);
        String prompt = PromptAssembler.buildSystemPrompt(context);
        assertTrue(prompt.contains("林晚晴"));
        assertTrue(prompt.contains("char-player"));
        assertTrue(prompt.contains("张三"));
    }

    @Test
    public void systemPrompt_observerMode_declaresNarrator() {
        PlayerIdentity identity = new PlayerIdentity("script-1",
                PlayerIdentity.RoleType.OBSERVER, null, 0L);
        AiContext context = new AiContext("script-1", null, new ArrayList<>(),
                identity, null, "叙述", "中文", 8);
        String prompt = PromptAssembler.buildSystemPrompt(context);
        assertTrue(prompt.contains("旁观者"));
    }

    @Test
    public void contextWindow_keepsMessagesInChronologicalOrder() {
        ChatMessage older = ChatMessage.builder().id("m1").scriptId("script-1")
                .type(ChatMessage.Type.CHARACTER_TEXT).side(ChatMessage.Side.MINE)
                .senderDisplayName("玩家").content("先说").sequence(1).createdAt(1).build();
        ChatMessage newer = ChatMessage.builder().id("m2").scriptId("script-1")
                .type(ChatMessage.Type.CHARACTER_TEXT).side(ChatMessage.Side.THEIRS)
                .senderDisplayName("NPC").content("后说").sequence(2).createdAt(2).build();

        assertEquals("玩家：先说\nNPC：后说",
                ContextWindowPolicy.toConversationText(Arrays.asList(older, newer), 40));
    }

    @Test
    public void promptContext_keepsEarlierStoryOutsideRecentWindow() {
        ChatMessage promise = message("m1", 1, "玩家", "把青铜钥匙交给了张三");
        ChatMessage middle = message("m2", 2, "张三", "我会保管它");
        ChatMessage latest = message("m3", 3, "玩家", "我们现在去码头");

        String context = ContextWindowPolicy.toPromptContext(
                Arrays.asList(promise, middle, latest), 1);

        assertTrue(context.contains("【长期剧情记忆】"));
        assertTrue(context.contains("把青铜钥匙交给了张三"));
        assertTrue(context.contains("【最近对话】"));
        assertTrue(context.contains("我们现在去码头"));
    }

    @Test
    public void promptContext_preservesBeginningAndPreWindowStateWhenMemoryIsCompacted() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(message("m1", 1, "玩家", "开端约定：日落前在钟楼会合"));
        for (int i = 2; i < 60; i++) {
            messages.add(message("m" + i, i, "NPC", repeat("中间剧情", 30)));
        }
        messages.add(message("m60", 60, "NPC", "当前状态：已经拿到地图"));
        messages.add(message("m61", 61, "玩家", "继续前进"));

        String context = ContextWindowPolicy.toPromptContext(messages, 1);

        assertTrue(context.contains("开端约定：日落前在钟楼会合"));
        assertTrue(context.contains("当前状态：已经拿到地图"));
        assertTrue(context.contains("继续前进"));
    }

    @Test
    public void systemPrompt_mentionsTargetAndDisablesExtraTurns() {
        CharacterProfile target = profile("char-target", "张三");
        AiContext context = new AiContext("script-1", null,
                new ArrayList<>(Arrays.asList(target)), null, null,
                "玩家：@张三 你好", "中文", 8, target);

        String prompt = PromptAssembler.buildSystemPrompt(context);

        assertTrue(prompt.contains("本轮只能由该角色发出 character_turn"));
        assertTrue(prompt.contains("continue_scene 必须为 false"));
    }

    // ---------- 剧本级扮演要求（长期生效） ----------

    @Test
    public void systemPrompt_styleDirective_appearsBeforeWorldAndProtocol() {
        AiContext context = contextWithRules("文风简练，禁止替玩家角色做决定", 2, null);

        String prompt = PromptAssembler.buildSystemPrompt(context);

        assertTrue(prompt.contains("【用户扮演要求"));
        assertTrue(prompt.contains("文风简练，禁止替玩家角色做决定"));
        // 用户显式指令位置靠前：协议之后、世界观/玩家身份等段落之前
        assertTrue(prompt.indexOf("你是一个多角色扮演群聊系统的导演与角色扮演引擎")
                < prompt.indexOf("【用户扮演要求"));
        int worldIndex = prompt.indexOf("【世界观】");
        assertTrue(worldIndex == -1 || prompt.indexOf("【用户扮演要求") < worldIndex);
        assertTrue(prompt.indexOf("【用户扮演要求") < prompt.indexOf("【玩家身份】"));
    }

    @Test
    public void systemPrompt_noStyleDirective_omitsSection() {
        AiContext context = contextWithRules(null, 2, null);

        String prompt = PromptAssembler.buildSystemPrompt(context);

        assertFalse(prompt.contains("【用户扮演要求"));
    }

    // ---------- 回复节奏约束 ----------

    @Test
    public void systemPrompt_paceConstraints_includeQuotaAndRecentSpeakers() {
        AiContext context = contextWithRules(null, 3,
                new ArrayList<>(Arrays.asList("张三", "李四")));

        String prompt = PromptAssembler.buildSystemPrompt(context);

        assertTrue(prompt.contains("【回复节奏约束】"));
        assertTrue(prompt.contains("本轮最多 3 名角色回复"));
        assertTrue(prompt.contains("张三、李四"));
    }

    @Test
    public void systemPrompt_paceConstraints_omittedRecentSpeakersWhenEmpty() {
        AiContext context = contextWithRules(null, 2, new ArrayList<>());

        String prompt = PromptAssembler.buildSystemPrompt(context);

        assertTrue(prompt.contains("本轮最多 2 名角色回复"));
        assertFalse(prompt.contains("以下角色最近刚刚发言"));
    }

    // ---------- 角色卡补全：backstory / system_prompt / sample_lines ----------

    @Test
    public void systemPrompt_characterCard_includesBackstorySampleLinesAndSystemPrompt() {
        CharacterProfile npc = new CharacterProfile("char-npc", "script-1", "张三",
                new ArrayList<>(), null, null, null, "温和",
                "他出生在边境小镇，后来成为商队护卫。", "简洁",
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new LinkedHashMap<>(), new ArrayList<>(Arrays.asList("哈哈，又见面了。")),
                "始终以商人视角思考。", null, true, 0, 0, 0, null);
        AiContext context = new AiContext("script-1", null,
                new ArrayList<>(Arrays.asList(npc)), null, null, "你好", "中文", 8);

        String prompt = PromptAssembler.buildSystemPrompt(context);

        assertTrue(prompt.contains("他出生在边境小镇，后来成为商队护卫。"));
        assertTrue(prompt.contains("哈哈，又见面了。"));
        assertTrue(prompt.contains("始终以商人视角思考。"));
    }

    @Test
    public void systemPrompt_characterCard_truncatesLongBackstory() {
        CharacterProfile npc = new CharacterProfile("char-npc", "script-1", "张三",
                new ArrayList<>(), null, null, null, "温和",
                repeat("背景", 200), "简洁",
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new LinkedHashMap<>(), new ArrayList<>(), null, null, true, 0, 0, 0, null);
        AiContext context = new AiContext("script-1", null,
                new ArrayList<>(Arrays.asList(npc)), null, null, "你好", "中文", 8);

        String prompt = PromptAssembler.buildSystemPrompt(context);

        assertFalse("backstory 截断到 300 字符", prompt.contains(repeat("背景", 200)));
        assertTrue(prompt.contains(repeat("背景", 150)));
    }

    private AiContext contextWithRules(String styleDirective, int maxResponders,
                                       List<String> recentSpeakerNames) {
        return new AiContext("script-1", null, new ArrayList<>(), null, null,
                "最近对话", "中文", 8, null, false,
                maxResponders, styleDirective, recentSpeakerNames);
    }

    private CharacterProfile profile(String id, String name) {
        return new CharacterProfile(id, "script-1", name, new ArrayList<>(), null,
                null, null, "温和", "背景", "简洁",
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new LinkedHashMap<>(), new ArrayList<>(), null, null, true, 0, 0, 0, null);
    }

    private ChatMessage message(String id, long sequence, String sender, String content) {
        return ChatMessage.builder().id(id).scriptId("script-1")
                .type(ChatMessage.Type.CHARACTER_TEXT).side(ChatMessage.Side.THEIRS)
                .senderDisplayName(sender).content(content).sequence(sequence).createdAt(sequence).build();
    }

    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }
}
