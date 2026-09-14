package com.appfactory.opencodechatbot.provider;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable provider configuration. Pure data — no Android dependencies.
 * Holds everything needed to talk to a single OpenAI-compatible chat provider.
 */
public final class ProviderConfig {

    public static final float DEFAULT_TEMPERATURE = 0.7f;
    public static final int DEFAULT_MAX_TOKENS = 2048;
    public static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant.";

    private final String providerName;
    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final float temperature;
    private final int maxTokens;

    public ProviderConfig(String providerName, String endpoint, String apiKey,
                          String model, float temperature, int maxTokens) {
        this.providerName = providerName;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    public static ProviderConfig empty() {
        return new ProviderConfig("", "", "", "", DEFAULT_TEMPERATURE, DEFAULT_MAX_TOKENS);
    }

    public String getProviderName() {
        return providerName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getModel() {
        return model;
    }

    public float getTemperature() {
        return temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public boolean isConfigured() {
        return providerName != null && !providerName.trim().isEmpty()
                && endpoint != null && !endpoint.trim().isEmpty();
    }

    /**
     * Normalize a user-supplied base URL into a full chat/completions endpoint.
     * Accepts either a bare base url ("https://api.openai.com/v1") or a url
     * that already ends in /chat/completions ("" or with a trailing slash).
     */
    public String chatCompletionsUrl() {
        return chatCompletionsUrl(endpoint);
    }

    public static String chatCompletionsUrl(String base) {
        if (base == null) {
            return "";
        }
        String s = base.trim();
        if (s.isEmpty()) {
            return "";
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.endsWith("/chat/completions")) {
            return s;
        }
        return s + "/chat/completions";
    }

    /**
     * Bundled provider presets. These are suggestions only — endpoint and API
     * key always come from the user's settings and are never hard-coded.
     */
    public static final class Preset {
        public final String label;
        public final String endpoint;
        public final String model;

        Preset(String label, String endpoint, String model) {
            this.label = label;
            this.endpoint = endpoint;
            this.model = model;
        }
    }

    private static final List<Preset> PRESETS = new ArrayList<Preset>();

    static {
        // Big Pickle via OpenCode Zen: the OpenAI-compatible gateway. The raw
        // HTTP API expects the bare model id "big-pickle" (the "opencode/"
        // prefix is only used inside the OpenCode client's own config, not in
        // the /v1/chat/completions request body). The API key is NOT preset —
        // it always comes from the user's settings and is never hard-coded.
        PRESETS.add(new Preset("OpenCode Zen (Big Pickle)", "https://opencode.ai/zen/v1", "big-pickle"));
        PRESETS.add(new Preset("OpenAI", "https://api.openai.com/v1", "gpt-3.5-turbo"));
        PRESETS.add(new Preset("OpenRouter", "https://openrouter.ai/api/v1", "openrouter/auto"));
        PRESETS.add(new Preset("Local (LM Studio)", "http://127.0.0.1:1234/v1", "local-model"));
        PRESETS.add(new Preset("Custom (OpenAI-compatible)", "", ""));
    }

    public static List<Preset> presets() {
        return PRESETS;
    }

    public static Preset preset(int index) {
        if (index < 0 || index >= PRESETS.size()) {
            return PRESETS.get(0);
        }
        return PRESETS.get(index);
    }
}