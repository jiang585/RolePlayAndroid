package com.example.roleplaychat.domain.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.roleplaychat.domain.model.AiBatch;
import com.example.roleplaychat.domain.model.AiEvent;
import com.example.roleplaychat.domain.model.CharacterProfile;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class AiOutputValidatorTest {

    @Test
    public void normalizeCharacterReferences_keepsUnresolvableTurnAsNarration() {
        AiBatch batch = new AiBatch("request-1", "script-1", Collections.singletonList(
                new AiEvent("event-1", AiEvent.Type.CHARACTER_TURN, "不存在的角色",
                        "（有人从门外推门而入）", 0)), false);

        AiBatch normalized = AiOutputValidator.normalizeCharacterReferences(batch,
                Collections.<CharacterProfile>emptyList());

        assertEquals(1, normalized.getEvents().size());
        AiEvent event = normalized.getEvents().get(0);
        assertEquals(AiEvent.Type.NARRATION, event.getType());
        assertNull(event.getCharacterId());
        assertEquals("（有人从门外推门而入）", event.getContent());
    }

    // ---------- capResponders（每轮回复人数硬约束） ----------

    @Test
    public void capResponders_keepsFirstDistinctSpeakersInOrder() {
        AiBatch batch = new AiBatch("request-1", "script-1", Arrays.asList(
                turn("e1", "char-a", "甲发言"),
                narration("e2", "旁白"),
                turn("e3", "char-b", "乙发言"),
                turn("e4", "char-c", "丙发言"),
                turn("e5", "char-a", "甲再次发言")), false);

        AiBatch capped = AiOutputValidator.capResponders(batch, 2);

        // e1(甲) 保留、e2 旁白保留、e3(乙) 保留；e4(丙) 超名额丢弃；e5(甲) 同角色重复发言丢弃
        assertEquals(3, capped.getEvents().size());
        assertEquals("e1", capped.getEvents().get(0).getEventId());
        assertEquals("e2", capped.getEvents().get(1).getEventId());
        assertEquals("e3", capped.getEvents().get(2).getEventId());
        assertFalse("有事件被裁掉，必须停止续演", capped.shouldContinueScene());
    }

    @Test
    public void capResponders_droppingEventsDisablesContinueScene() {
        AiBatch batch = new AiBatch("request-1", "script-1", Arrays.asList(
                turn("e1", "char-a", "甲发言"),
                turn("e2", "char-b", "乙发言"),
                turn("e3", "char-c", "丙发言")), true);

        AiBatch capped = AiOutputValidator.capResponders(batch, 2);

        assertEquals(2, capped.getEvents().size());
        assertFalse("有事件被裁掉时必须停止续演", capped.shouldContinueScene());
    }

    @Test
    public void capResponders_narrationEventsUnaffectedByQuota() {
        AiBatch batch = new AiBatch("request-1", "script-1", Arrays.asList(
                narration("e1", "夜幕降临"),
                narration("e2", "风声渐起"),
                turn("e3", "char-a", "甲发言"),
                turn("e4", "char-b", "乙发言")), true);

        AiBatch capped = AiOutputValidator.capResponders(batch, 2);

        assertEquals(4, capped.getEvents().size());
        assertTrue(capped.shouldContinueScene());
    }

    @Test
    public void capResponders_singleSpeakerAllowedByLargeQuota() {
        AiBatch batch = new AiBatch("request-1", "script-1", Collections.singletonList(
                turn("e1", "char-a", "甲发言")));

        AiBatch capped = AiOutputValidator.capResponders(batch, 1);

        assertEquals(1, capped.getEvents().size());
        assertEquals("char-a", capped.getEvents().get(0).getCharacterId());
    }

    private static AiEvent turn(String eventId, String characterId, String content) {
        return new AiEvent(eventId, AiEvent.Type.CHARACTER_TURN, characterId, content, 0);
    }

    private static AiEvent narration(String eventId, String content) {
        return new AiEvent(eventId, AiEvent.Type.NARRATION, null, content, 0);
    }
}
