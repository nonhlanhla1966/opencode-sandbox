package com.appfactory.opencodechatbot.provider;

import com.appfactory.opencodechatbot.model.Message;
import com.appfactory.opencodechatbot.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the JSON body for an OpenAI-compatible {@code /chat/completions}
 * request. Pure logic — unit-testable without Android or network access.
 */
public final class ChatCompletionsRequest {

    private ChatCompletionsRequest() {
    }

    /**
     * Build the request body map. Includes only the parameters the provider
     * should see; never includes the API key (that travels in the header).
     */
    public static Map<String, Object> buildBody(ProviderConfig config,
                                                 List<Message> messages,
                                                 String systemPrompt,
                                                 boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel() == null || config.getModel().trim().isEmpty()
                ? "gpt-3.5-turbo"
                : config.getModel().trim());
        body.put("messages", messagesPayload(messages, systemPrompt));
        body.put("stream", stream);
        float temperature = config.getTemperature();
        if (temperature >= 0f && temperature <= 2f) {
            body.put("temperature", temperature);
        }
        if (config.getMaxTokens() > 0) {
            body.put("max_tokens", config.getMaxTokens());
        }
        return body;
    }

    public static List<Object> messagesPayload(List<Message> messages, String systemPrompt) {
        List<Object> payload = new ArrayList<>();
        String system = systemPrompt == null ? "" : systemPrompt.trim();
        if (!system.isEmpty()) {
            payload.add(roleMessage("system", system));
        }
        if (messages != null) {
            for (Message m : messages) {
                // Never forward masked/sending/error placeholders to the model.
                if (m.getRole() == null || m.getContent() == null) {
                    continue;
                }
                if (m.isGenerating() || m.isError()) {
                    continue;
                }
                if (m.isUser() || Message.ROLE_SYSTEM.equals(m.getRole()) || Message.ROLE_ASSISTANT.equals(m.getRole())) {
                    if (m.getContent().isEmpty()) {
                        continue;
                    }
                    payload.add(roleMessage(m.getRole(), m.getContent()));
                }
            }
        }
        return payload;
    }

    private static Object roleMessage(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    public static String buildJson(Map<String, Object> body) {
        return Json.stringify(body);
    }
}