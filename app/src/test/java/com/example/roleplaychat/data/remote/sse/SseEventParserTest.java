package com.example.roleplaychat.data.remote.sse;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SseEventParserTest {

    @Test
    public void doneEvent_isExposedAsTerminalState() {
        SseEventParser parser = new SseEventParser();

        assertNull(parser.onLine("data: [DONE]"));
        assertFalse(parser.isDone());
        assertNull(parser.onLine(""));

        assertTrue(parser.isDone());
    }

    @Test
    public void doneAtEndOfStream_isRecognizedByFinish() {
        SseEventParser parser = new SseEventParser();

        parser.onLine("data: [DONE]");
        assertNull(parser.finish());

        assertTrue(parser.isDone());
    }
}
