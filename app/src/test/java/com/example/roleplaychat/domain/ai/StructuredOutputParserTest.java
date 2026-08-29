package com.example.roleplaychat.domain.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.roleplaychat.domain.model.AiBatch;
import com.example.roleplaychat.domain.model.AiEvent;
import com.example.roleplaychat.domain.model.AiRequest;
import com.example.roleplaychat.domain.model.PromptMessage;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * 结构化输出解析器测试（架构文档 §15.2-6：非法 JSON、Markdown 包裹、
 * 未知角色与超长事件不会导致崩溃）。
 */
public class StructuredOutputParserTest {

    private static final String REQUEST_ID = "req-123";

    @Test
    public void parse_validJson_returnsBatch() throws Exception {
        String raw = "{\"schema_version\":1,\"request_id\":\"req-123\",\"continue_scene\":true,\"events\":["
                + "{\"event_id\":\"e1\",\"type\":\"narration\",\"content\":\"（张三笑了笑）\"},"
                + "{\"event_id\":\"e2\",\"type\":\"character_turn\",\"character_id\":\"char-1\",\"content\":\"你好啊\"}"
                + "]}";
        AiBatch batch = StructuredOutputParser.parse(raw, REQUEST_ID, "script-1");
        assertEquals(2, batch.getEvents().size());
        assertEquals(AiEvent.Type.NARRATION, batch.getEvents().get(0).getType());
        assertEquals(AiEvent.Type.CHARACTER_TURN, batch.getEvents().get(1).getType());
        assertEquals("char-1", batch.getEvents().get(1).getCharacterId());
        assertEquals("req-123:e1", batch.getEvents().get(0).getEventId());
        assertEquals("req-123:e2", batch.getEvents().get(1).getEventId());
        assertTrue(batch.shouldContinueScene());
    }

    @Test
    public void parse_markdownFencedJson_extracts() throws Exception {
        String raw = "```json\n{\"schema_version\":1,\"request_id\":\"req-123\",\"events\":["
                + "{\"event_id\":\"e1\",\"type\":\"narration\",\"content\":\"旁白\"}]}\n```";
        AiBatch batch = StructuredOutputParser.parse(raw, REQUEST_ID, "script-1");
        assertEquals(1, batch.getEvents().size());
    }

    @Test
    public void parse_unknownType_dropsEvent() throws Exception {
        String raw = "{\"schema_version\":1,\"request_id\":\"req-123\",\"events\":["
                + "{\"event_id\":\"e1\",\"type\":\"unknown_type\",\"content\":\"x\"},"
                + "{\"event_id\":\"e2\",\"type\":\"narration\",\"content\":\"有效\"}"
                + "]}";
        AiBatch batch = StructuredOutputParser.parse(raw, REQUEST_ID, "script-1");
        assertEquals(1, batch.getEvents().size());
        assertEquals(AiEvent.Type.NARRATION, batch.getEvents().get(0).getType());
    }

    @Test(expected = StructuredOutputParser.OutputInvalidException.class)
    public void parse_invalidJson_throws() throws Exception {
        StructuredOutputParser.parse("{not json", REQUEST_ID, "script-1");
    }

    @Test(expected = StructuredOutputParser.OutputInvalidException.class)
    public void parse_emptyEvents_throws() throws Exception {
        StructuredOutputParser.parse("{\"schema_version\":1,\"request_id\":\"req-123\",\"events\":[]}",
                REQUEST_ID, "script-1");
    }

    @Test
    public void parse_requestIdMismatch_usesTrustedLocalRequestId() throws Exception {
        String raw = "{\"schema_version\":1,\"request_id\":\"different\",\"events\":["
                + "{\"event_id\":\"e1\",\"type\":\"narration\",\"content\":\"x\"}]}";
        AiBatch batch = StructuredOutputParser.parse(raw, REQUEST_ID, "script-1");
        assertEquals(REQUEST_ID, batch.getRequestId());
    }

    @Test
    public void parse_sameModelEventIdAcrossRequests_producesUniqueLocalIds() throws Exception {
        String raw = "{\"schema_version\":1,\"events\":["
                + "{\"event_id\":\"e1\",\"type\":\"narration\",\"content\":\"x\"}]}";

        AiBatch first = StructuredOutputParser.parse(raw, "req-1", "script-1");
        AiBatch second = StructuredOutputParser.parse(raw, "req-2", "script-1");

        assertEquals("req-1:e1", first.getEvents().get(0).getEventId());
        assertEquals("req-2:e1", second.getEvents().get(0).getEventId());
    }

    @Test
    public void fallbackText_extractsContentFromMalformedJson() {
        String text = StructuredOutputParser.fallbackText(
                "{\"events\":[{\"type\":\"character_turn\",\"content\":\"你好，先坐。\"}," );
        assertEquals("你好，先坐。", text);
    }

    @Test
    public void fallbackText_stripsMarkdownFence() {
        assertEquals("模型直接回复", StructuredOutputParser.fallbackText(
                "```text\n模型直接回复\n```"));
    }
}
