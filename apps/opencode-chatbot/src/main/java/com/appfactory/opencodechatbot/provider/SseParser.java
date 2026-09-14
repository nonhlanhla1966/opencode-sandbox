package com.appfactory.opencodechatbot.provider;

import com.appfactory.opencodechatbot.util.Json;

import java.util.List;
import java.util.Map;

/**
 * Parses Server-Sent-Events (SSE) lines produced by streaming
 * OpenAI-compatible chat endpoints. Pure logic — JVM unit-testable.
 */
public final class SseParser {

    private SseParser() {
    }

    /** A single parsed event fragment from a streaming line. */
    public static final class Fragment {
        /** Text delta (may be empty). */
        public final String text;
        /** True when the stream is done ([DONE] or a streaming separator). */
        public final boolean done;
        /** Non-empty when this line carried an explicit error payload. */
        public final String error;

        Fragment(String text, boolean done, String error) {
            this.text = text;
            this.done = done;
            this.error = error;
        }
    }

    public static final Fragment DONE = new Fragment("", true, "");
    public static final Fragment EMPTY = new Fragment("", false, "");

    /**
     * Parse a raw SSE line ("data: {...}" or "data: [DONE]").
     * Returns null for comment lines / irrelevant lines.
     */
    public static Fragment parseLine(String rawLine) {
        if (rawLine == null) {
            return null;
        }
        String line = rawLine.trim();
        if (line.isEmpty() || line.startsWith(":") || line.startsWith("event:")) {
            return null;
        }
        if (!line.startsWith("data:")) {
            return null;
        }
        String data = line.substring("data:".length()).trim();
        if (data.isEmpty()) {
            return EMPTY;
        }
        if (data.equals("[DONE]")) {
            return DONE;
        }
        Map<String, Object> json;
        try {
            Object parsed = Json.parse(data);
            if (!(parsed instanceof Map)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> obj = (Map<String, Object>) parsed;
            json = obj;
        } catch (RuntimeException e) {
            return new Fragment("", false, "Invalid stream chunk: " + data);
        }
        String error = extractError(json);
        if (!error.isEmpty()) {
            return new Fragment("", false, error);
        }
        String content = extractDelta(json);
        return new Fragment(content == null ? "" : content, false, "");
    }

    static String extractDelta(Map<String, Object> json) {
        Object choices = json.get("choices");
        if (choices instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) choices;
            if (!list.isEmpty() && list.get(0) instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> choice = (Map<String, Object>) list.get(0);
                Object delta = choice.get("delta");
                if (delta instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> deltaMap = (Map<String, Object>) delta;
                    if (deltaMap.get("content") != null) {
                        return String.valueOf(deltaMap.get("content"));
                    }
                    if (deltaMap.get("reasoning_content") != null) {
                        return String.valueOf(deltaMap.get("reasoning_content"));
                    }
                    return "";
                }
                Object text = choice.get("text");
                if (text != null) {
                    return String.valueOf(text);
                }
                if (choice.containsKey("finish_reason")) {
                    return "";
                }
            }
        }
        return "";
    }

    static String extractError(Map<String, Object> json) {
        Object error = json.get("error");
        if (error instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> err = (Map<String, Object>) error;
            Object message = err.get("message");
            if (message != null) {
                return String.valueOf(message);
            }
        }
        return "";
    }

    /**
     * Extract the content from a non-streaming chat completions response.
     */
    public static String extractFinalContent(String responseJson) {
        if (responseJson == null || responseJson.trim().isEmpty()) {
            return "";
        }
        Map<String, Object> json;
        try {
            Object parsed = Json.parse(responseJson);
            if (!(parsed instanceof Map)) {
                return "";
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> obj = (Map<String, Object>) parsed;
            json = obj;
        } catch (RuntimeException e) {
            return "";
        }
        String error = extractError(json);
        if (!error.isEmpty()) {
            return "API error: " + error;
        }
        Object choices = json.get("choices");
        if (choices instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) choices;
            if (!list.isEmpty() && list.get(0) instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> choice = (Map<String, Object>) list.get(0);
                Object message = choice.get("message");
                if (message instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> messageMap = (Map<String, Object>) message;
                    if (messageMap.get("content") != null) {
                        return String.valueOf(messageMap.get("content"));
                    }
                }
            }
        }
        return "";
    }
}