package com.appfactory.opencodechatbot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.appfactory.opencodechatbot.model.Message;
import com.appfactory.opencodechatbot.provider.ChatCompletionsRequest;
import com.appfactory.opencodechatbot.provider.ProviderConfig;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChatCompletionsRequestTest {

    private ProviderConfig config() {
        return new ProviderConfig("test", "https://example.com/v1", "", "opencode/big-pickle", 0.8f, 2048);
    }

    @Test
    public void bodyContainsModelStreamAndMessages() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("Hi"));
        Map<String, Object> body = ChatCompletionsRequest.buildBody(config(), messages, "", true);
        assertEquals("opencode/big-pickle", body.get("model"));
        assertEquals(Boolean.TRUE, body.get("stream"));
        assertEquals(0.8f, ((Number) body.get("temperature")).floatValue(), 0.001f);
        assertEquals(2048, body.get("max_tokens"));
        assertTrue(body.get("messages") instanceof List);
    }

    @Test
    public void systemPromptBecomesFirstSystemMessage() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("Hello"));
        List<Object> payload = ChatCompletionsRequest.messagesPayload(messages, "You are a builder");
        assertEquals(2, payload.size());
        Map<?, ?> first = (Map<?, ?>) payload.get(0);
        assertEquals("system", first.get("role"));
        assertEquals("You are a builder", first.get("content"));
    }

    @Test
    public void omitsEmptySystemPrompt() {
        List<Object> payload = ChatCompletionsRequest.messagesPayload(
                List.of(Message.user("Hi")), "   ");
        assertEquals(1, payload.size());
        Map<?, ?> first = (Map<?, ?>) payload.get(0);
        assertEquals("user", first.get("role"));
    }

    @Test
    public void skipsMaskedSendingAndErrorPlaceholders() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.assistant("partial", Message.STATUS_SENDING));
        messages.add(Message.assistant("done", Message.STATUS_DONE));
        messages.add(Message.user(""));
        List<Object> payload = ChatCompletionsRequest.messagesPayload(messages, "");
        assertEquals(1, payload.size());
        Map<?, ?> only = (Map<?, ?>) payload.get(0);
        assertEquals("assistant", only.get("role"));
        assertEquals("done", only.get("content"));
    }

    @Test
    public void userMessageHelperMarksUserRole() {
        Message m = Message.user("q");
        assertTrue(m.isUser());
        assertFalse(Message.assistant("a", Message.STATUS_DONE).isUser());
    }

    @Test
    public void defaultModelWhenBlank() {
        ProviderConfig c = new ProviderConfig("test", "https://example.com/v1", "", "   ", 0.7f, 2048);
        Map<String, Object> body = ChatCompletionsRequest.buildBody(c, new ArrayList<>(), "", false);
        assertEquals("gpt-3.5-turbo", body.get("model"));
    }

    @Test
    public void jsonBodyNeverContainsApiKey() {
        ProviderConfig c = new ProviderConfig("test", "https://example.com/v1", "sk-super-secret", "m", 0.7f, 2048);
        String json = ChatCompletionsRequest.buildJson(
                ChatCompletionsRequest.buildBody(c, new ArrayList<>(), "", true));
        assertFalse(json.toLowerCase().contains("secret"));
        assertFalse(json.toLowerCase().contains("api_key"));
        assertFalse(json.toLowerCase().contains("authorization"));
    }
}