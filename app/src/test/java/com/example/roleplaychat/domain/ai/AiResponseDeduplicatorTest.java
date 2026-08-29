package com.example.roleplaychat.domain.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.example.roleplaychat.domain.model.AiBatch;
import com.example.roleplaychat.domain.model.AiEvent;
import com.example.roleplaychat.domain.model.ChatMessage;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class AiResponseDeduplicatorTest {

    @Test
    public void removesNearDuplicateCharacterReplyFromAutomaticAdvance() {
        ChatMessage previous = ChatMessage.builder()
                .id("old").scriptId("script-1").characterId("桃桃")
                .type(ChatMessage.Type.CHARACTER_TEXT).side(ChatMessage.Side.THEIRS)
                .content("哼！笨蛋江！你突然说什么傻话！不过既然你这么说了，我也不是不能考虑一下！")
                .sequence(2).status(ChatMessage.Status.DONE).build();
        AiBatch candidate = new AiBatch("request-2", "script-1", Collections.singletonList(
                new AiEvent("event-2", AiEvent.Type.CHARACTER_TURN, "桃桃",
                        "哼！笨、笨蛋江！你突然说什么傻话！不过既然你这么说了，我也不是不能考虑一下！", 0)), true);

        AiBatch result = AiResponseDeduplicator.removeNearDuplicates(candidate,
                Collections.singletonList(previous));

        assertTrueEmpty(result);
        assertFalse(result.shouldContinueScene());
    }

    @Test
    public void keepsNewReplyFromSameCharacter() {
        ChatMessage previous = ChatMessage.builder()
                .id("old").scriptId("script-1").characterId("桃桃")
                .type(ChatMessage.Type.CHARACTER_TEXT).side(ChatMessage.Side.THEIRS)
                .content("我会考虑一下。")
                .sequence(2).status(ChatMessage.Status.DONE).build();
        AiBatch candidate = new AiBatch("request-2", "script-1", Arrays.asList(
                new AiEvent("event-2", AiEvent.Type.CHARACTER_TURN, "桃桃", "那我们现在就出发吧。", 0)), true);

        AiBatch result = AiResponseDeduplicator.removeNearDuplicates(candidate,
                Collections.singletonList(previous));

        assertEquals(1, result.getEvents().size());
        assertEquals("那我们现在就出发吧。", result.getEvents().get(0).getContent());
    }

    @Test
    public void removesDuplicateSystemEventWithinSameBatch() {
        AiBatch candidate = new AiBatch("request-2", "script-1", Arrays.asList(
                new AiEvent("event-1", AiEvent.Type.SYSTEM_EVENT, null, "【系统】桃桃加入群聊", 0),
                new AiEvent("event-2", AiEvent.Type.SYSTEM_EVENT, null, "【系统】桃桃加入群聊", 1)), false);

        AiBatch result = AiResponseDeduplicator.removeNearDuplicates(candidate,
                Collections.<ChatMessage>emptyList());

        assertEquals(1, result.getEvents().size());
        assertEquals("【系统】桃桃加入群聊", result.getEvents().get(0).getContent());
    }

    @Test
    public void removesDuplicateCharacterLineWithinSameBatch() {
        AiBatch candidate = new AiBatch("request-2", "script-1", Arrays.asList(
                new AiEvent("event-1", AiEvent.Type.CHARACTER_TURN, "桃桃", "我们走吧！", 0),
                new AiEvent("event-2", AiEvent.Type.CHARACTER_TURN, "桃桃", "我们走吧！", 1)), false);

        AiBatch result = AiResponseDeduplicator.removeNearDuplicates(candidate,
                Collections.<ChatMessage>emptyList());

        assertEquals(1, result.getEvents().size());
    }

    @Test
    public void keepsDistinctEventsWithinSameBatch() {
        AiBatch candidate = new AiBatch("request-2", "script-1", Arrays.asList(
                new AiEvent("event-1", AiEvent.Type.CHARACTER_TURN, "桃桃", "我们走吧！", 0),
                new AiEvent("event-2", AiEvent.Type.CHARACTER_TURN, "桃桃", "那我去准备一下。", 1),
                new AiEvent("event-3", AiEvent.Type.SYSTEM_EVENT, null, "【系统】天色渐暗", 2)), false);

        AiBatch result = AiResponseDeduplicator.removeNearDuplicates(candidate,
                Collections.<ChatMessage>emptyList());

        assertEquals(3, result.getEvents().size());
    }

    @Test
    public void removesNonAdjacentSystemEventRepeat() {
        ChatMessage firstSystem = ChatMessage.builder()
                .id("s1").scriptId("script-1")
                .type(ChatMessage.Type.SYSTEM_EVENT).side(ChatMessage.Side.THEIRS)
                .content("【系统】桃桃加入群聊").sequence(1).status(ChatMessage.Status.DONE).build();
        ChatMessage narration = ChatMessage.builder()
                .id("n1").scriptId("script-1")
                .type(ChatMessage.Type.NARRATION).side(ChatMessage.Side.THEIRS)
                .content("（桃花开始飘落）").sequence(2).status(ChatMessage.Status.DONE).build();
        ChatMessage lastSystem = ChatMessage.builder()
                .id("s2").scriptId("script-1")
                .type(ChatMessage.Type.SYSTEM_EVENT).side(ChatMessage.Side.THEIRS)
                .content("【系统】天色渐暗").sequence(3).status(ChatMessage.Status.DONE).build();
        AiBatch candidate = new AiBatch("request-2", "script-1", Collections.singletonList(
                new AiEvent("event-2", AiEvent.Type.SYSTEM_EVENT, null, "【系统】桃桃加入群聊", 0)), false);

        AiBatch result = AiResponseDeduplicator.removeNearDuplicates(candidate,
                Arrays.asList(firstSystem, narration, lastSystem));

        assertTrueEmpty(result);
    }

    @Test
    public void keepsNewSystemEventAfterDifferentSystemEvents() {
        ChatMessage lastSystem = ChatMessage.builder()
                .id("s1").scriptId("script-1")
                .type(ChatMessage.Type.SYSTEM_EVENT).side(ChatMessage.Side.THEIRS)
                .content("【系统】天色渐暗").sequence(1).status(ChatMessage.Status.DONE).build();
        AiBatch candidate = new AiBatch("request-2", "script-1", Collections.singletonList(
                new AiEvent("event-2", AiEvent.Type.SYSTEM_EVENT, null, "【系统】桃桃拿出地图", 0)), false);

        AiBatch result = AiResponseDeduplicator.removeNearDuplicates(candidate,
                Collections.singletonList(lastSystem));

        assertEquals(1, result.getEvents().size());
        assertEquals("【系统】桃桃拿出地图", result.getEvents().get(0).getContent());
    }

    private static void assertTrueEmpty(AiBatch batch) {
        assertEquals(0, batch.getEvents().size());
    }
}
