package com.example.roleplaychat.domain.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.roleplaychat.domain.model.AiBatch;
import com.example.roleplaychat.domain.model.AiEvent;
import com.example.roleplaychat.domain.model.CharacterProfile;

import org.junit.Test;

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
}
