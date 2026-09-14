package com.appfactory.opencodechatbot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.appfactory.opencodechatbot.provider.SseParser;

import org.junit.Test;

public class SseParserTest {

    @Test
    public void doneMarker() {
        SseParser.Fragment f = SseParser.parseLine("data: [DONE]");
        assertTrue(f.done);
        assertEquals("", f.text);
    }

    @Test
    public void extractsStreamingDelta() {
        SseParser.Fragment f = SseParser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}");
        assertFalse(f.done);
        assertEquals("Hel", f.text);
    }

    @Test
    public void extractsReasoningDelta() {
        SseParser.Fragment f = SseParser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"think\"}}]}");
        assertEquals("think", f.text);
    }

    @Test
    public void extractsLegacyTextDelta() {
        SseParser.Fragment f = SseParser.parseLine(
                "data: {\"choices\":[{\"text\":\"old styles\"}]}");
        assertEquals("old styles", f.text);
    }

    @Test
    public void extractsErrorPayload() {
        SseParser.Fragment f = SseParser.parseLine(
                "data: {\"error\":{\"message\":\"rate limited\"}}");
        assertTrue(f.error.contains("rate limited"));
    }

    @Test
    public void returnsEmptyDeltaForNonContentChunk() {
        SseParser.Fragment f = SseParser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}");
        assertFalse(f.done);
        assertEquals("", f.text);
    }

    @Test
    public void ignoresCommentKeepAliveAndEventLines() {
        assertNull(SseParser.parseLine(": ping"));
        assertNull(SseParser.parseLine("event: message"));
        assertNull(SseParser.parseLine(""));
        assertNull(SseParser.parseLine("random: not data"));
    }

    @Test
    public void marksUnparseableChunkAsError() {
        SseParser.Fragment f = SseParser.parseLine("data: {broken json");
        assertEquals("", f.text);
        assertTrue(f.error.length() > 0);
    }

    @Test
    public void dataWithEmptyValueReturnsEmpty() {
        SseParser.Fragment f = SseParser.parseLine("data: ");
        assertFalse(f.done);
        assertEquals("", f.text);
    }

    @Test
    public void finalContentFromNonStreamingResponse() {
        String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":\"Full answer\"}}]}";
        assertEquals("Full answer", SseParser.extractFinalContent(json));
    }

    @Test
    public void finalContentEmptyWhenMissing() {
        assertEquals("", SseParser.extractFinalContent("{\"choices\":[]}"));
        assertEquals("", SseParser.extractFinalContent("garbage"));
        assertEquals("", SseParser.extractFinalContent(""));
    }

    @Test
    public void finalContentSurfacesApiError() {
        String json = "{\"error\":{\"message\":\"bad key\"}}";
        assertTrue(SseParser.extractFinalContent(json).contains("bad key"));
    }
}