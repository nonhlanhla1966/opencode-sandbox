package com.appfactory.opencodechatbot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.appfactory.opencodechatbot.provider.ModelsFetcher;

import org.junit.Test;

import java.util.List;

public class ModelsFetcherTest {

    @Test
    public void modelsUrlFromBareBase() {
        assertEquals("https://opencode.ai/zen/v1/models",
                ModelsFetcher.modelsUrl("https://opencode.ai/zen/v1"));
    }

    @Test
    public void modelsUrlFromTrailingSlashBase() {
        assertEquals("https://opencode.ai/zen/v1/models",
                ModelsFetcher.modelsUrl("https://opencode.ai/zen/v1/"));
    }

    @Test
    public void modelsUrlFromFullChatCompletionsUrl() {
        assertEquals("https://opencode.ai/zen/v1/models",
                ModelsFetcher.modelsUrl("https://opencode.ai/zen/v1/chat/completions"));
    }

    @Test
    public void parseOpenCodeZenModelsResponseFindsBigPickle() {
        String json = "{\"object\":\"list\",\"data\":["
                + "{\"id\":\"big-pickle\",\"object\":\"model\",\"owned_by\":\"opencode\"},"
                + "{\"id\":\"gpt-5.5\",\"object\":\"model\",\"owned_by\":\"opencode\"},"
                + "{\"id\":\"claude-sonnet-4-6\",\"object\":\"model\",\"owned_by\":\"opencode\"}"
                + "]}";
        List<String> ids = ModelsFetcher.parseModelIds(json);
        assertEquals(3, ids.size());
        assertTrue(ids.contains("big-pickle"));
        assertTrue(ids.contains("gpt-5.5"));
        assertTrue(ids.contains("claude-sonnet-4-6"));
    }

    @Test
    public void parseBadInputReturnsEmptyList() {
        assertTrue(ModelsFetcher.parseModelIds(null).isEmpty());
        assertTrue(ModelsFetcher.parseModelIds("").isEmpty());
        assertTrue(ModelsFetcher.parseModelIds("not json").isEmpty());
        assertTrue(ModelsFetcher.parseModelIds("{\"data\":[]}").isEmpty());
    }
}