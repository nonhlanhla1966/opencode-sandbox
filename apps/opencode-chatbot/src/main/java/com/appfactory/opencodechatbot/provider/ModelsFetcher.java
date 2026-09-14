package com.appfactory.opencodechatbot.provider;

import com.appfactory.opencodechatbot.util.Json;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Discovers the models a provider actually offers by calling the standard
 * OpenAI-compatible {@code GET {base}/models} endpoint. Pure logic (URL
 * building + response parsing) is JVM-unit-testable; the network call
 * deliberately keeps the API key only in the outgoing Authorization header and
 * never logs, prints, or includes it anywhere else.
 */
public final class ModelsFetcher {

    private ModelsFetcher() {
    }

    /**
     * Build the models URL from a user-supplied base endpoint. Accepts a bare
     * base url ("https://opencode.ai/zen/v1"), a url with a trailing slash,
     * or an already-complete /chat/completions url.
     */
    public static String modelsUrl(String base) {
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
            s = s.substring(0, s.length() - "/chat/completions".length());
            while (s.endsWith("/")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        return s + "/models";
    }

    /**
     * Parse model ids from an OpenAI-compatible {@code /models} response:
     * {@code {"object":"list","data":[{"id":"big-pickle",...}, ...]}}.
     */
    @SuppressWarnings("unchecked")
    public static List<String> parseModelIds(String json) {
        List<String> ids = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return ids;
        }
        try {
            Object parsed = Json.parse(json);
            if (!(parsed instanceof Map)) {
                return ids;
            }
            Object data = ((Map<String, Object>) parsed).get("data");
            if (data instanceof List) {
                for (Object item : (List<Object>) data) {
                    if (item instanceof Map) {
                        Object id = ((Map<String, Object>) item).get("id");
                        if (id != null && !String.valueOf(id).trim().isEmpty()) {
                            ids.add(String.valueOf(id).trim());
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            // Not JSON we understand — return an empty list.
        }
        return ids;
    }

    /**
     * GET {@code {base}/models} with Bearer auth and return the model ids.
     * Throws on network/http failure so the caller can surface a safe message.
     */
    public static List<String> fetch(String base, String apiKey) throws Exception {
        String url = modelsUrl(base);
        if (url.isEmpty()) {
            throw new IllegalArgumentException("Invalid endpoint");
        }
        HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
        try {
            http.setRequestMethod("GET");
            http.setConnectTimeout(15000);
            http.setReadTimeout(15000);
            http.setRequestProperty("Accept", "application/json");
            if (apiKey != null && !apiKey.isEmpty()) {
                http.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
            int code = http.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code);
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(http.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return parseModelIds(sb.toString());
        } finally {
            http.disconnect();
        }
    }
}