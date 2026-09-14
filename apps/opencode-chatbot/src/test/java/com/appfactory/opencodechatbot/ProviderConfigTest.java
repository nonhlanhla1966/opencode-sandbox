package com.appfactory.opencodechatbot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.appfactory.opencodechatbot.provider.ChatCompletionsRequest;
import com.appfactory.opencodechatbot.provider.ProviderConfig;
import com.appfactory.opencodechatbot.provider.ProviderConfig.Preset;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Proves the Big Pickle / OpenCode Zen preset builds the correct request. */
public class ProviderConfigTest {

    private Preset bigPicklePreset() {
        for (Preset p : ProviderConfig.presets()) {
            if (p.label.toLowerCase().contains("big pickle")) {
                return p;
            }
        }
        return null;
    }

    @Test
    public void bigPicklePresetExistsWithZenEndpointAndBareModelId() {
        Preset preset = bigPicklePreset();
        assertNotNull("Big Pickle preset must exist", preset);
        assertEquals("https://opencode.ai/zen/v1", preset.endpoint);
        assertEquals("big-pickle", preset.model);
    }

    @Test
    public void bigPickleConfigBuildsChatCompletionsUrl() {
        ProviderConfig config = new ProviderConfig(
                "OpenCode Zen", "https://opencode.ai/zen/v1",
                "z-…user-supplied…", "big-pickle", 0.7f, 2048);
        assertEquals("https://opencode.ai/zen/v1/chat/completions",
                config.chatCompletionsUrl());
    }

    @Test
    public void bigPickleConfigSendsExactSupportedModelId() {
        ProviderConfig config = new ProviderConfig(
                "OpenCode Zen", "https://opencode.ai/zen/v1", "",
                "big-pickle", 0.7f, 2048);
        Map<String, Object> body = ChatCompletionsRequest.buildBody(
                config, new ArrayList<>(), "You are a helpful assistant.", true);
        assertEquals("big-pickle", body.get("model"));
    }

    @Test
    public void existingPresetsStillPresent() {
        List<Preset> presets = ProviderConfig.presets();
        assertTrue(presets.size() >= 5);
        StringBuilder labels = new StringBuilder();
        for (Preset p : presets) {
            labels.append(p.label).append('|');
        }
        assertTrue(labels.toString().contains("OpenAI"));
        assertTrue(labels.toString().contains("OpenRouter"));
        assertTrue(labels.toString().contains("Local"));
        assertTrue(labels.toString().contains("Custom"));
    }
}