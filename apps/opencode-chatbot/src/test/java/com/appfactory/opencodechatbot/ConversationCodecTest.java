package com.appfactory.opencodechatbot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.appfactory.opencodechatbot.data.ConversationCodec;
import com.appfactory.opencodechatbot.model.Conversation;
import com.appfactory.opencodechatbot.model.Message;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ConversationCodecTest {

    @Test
    public void roundTripsConversation() {
        List<Conversation> conversations = new ArrayList<>();
        Conversation c = Conversation.create("My chat", "system prompt here");
        c.getMessages().add(Message.user("Hello"));
        c.getMessages().add(Message.assistant("Hi there", Message.STATUS_DONE));
        conversations.add(c);

        String json = ConversationCodec.encodeAll(conversations);
        ConversationCodec.Document doc = ConversationCodec.decodeAll(json);

        assertEquals(1, doc.conversations.size());
        Conversation decoded = doc.conversations.get(0);
        assertEquals(c.getId(), decoded.getId());
        assertEquals("My chat", decoded.getTitle());
        assertEquals("system prompt here", decoded.getSystemPrompt());
        assertEquals(2, decoded.getMessages().size());
        assertEquals("user", decoded.getMessages().get(0).getRole());
        assertEquals("Hello", decoded.getMessages().get(0).getContent());
        assertEquals("assistant", decoded.getMessages().get(1).getRole());
        assertEquals("Hi there", decoded.getMessages().get(1).getContent());
    }

    @Test
    public void decodeEmptyReturnsEmptyDocument() {
        ConversationCodec.Document doc = ConversationCodec.decodeAll("");
        assertNotNull(doc.conversations);
        assertTrue(doc.conversations.isEmpty());
    }

    @Test
    public void decodeGarbageIsLenient() {
        ConversationCodec.Document doc = ConversationCodec.decodeAll("not json at all");
        assertTrue(doc.conversations.isEmpty());
    }

    @Test
    public void titleDerivesFromFirstUserMessage() {
        Conversation c = Conversation.create("", "");
        c.getMessages().add(Message.user("Build me a calculator"));
        c.deriveTitle();
        assertEquals("Build me a calculator", c.getTitle());
    }

    @Test
    public void deriveTitleSkipsEmpty() {
        Conversation c = Conversation.create("", "");
        c.deriveTitle();
        assertEquals("New conversation", c.getTitle());
    }

    @Test
    public void codecIgnoresUnknownFields() {
        String json = "{\"v\":\"1\",\"conversations\":[{\"id\":\"abc\",\"title\":\"T\","
                + "\"systemPrompt\":\"\",\"createdAt\":1,\"updatedAt\":1,"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],"
                + "\"unexpected\":{\"nested\":1}}]}";
        ConversationCodec.Document doc = ConversationCodec.decodeAll(json);
        assertEquals(1, doc.conversations.size());
        assertEquals("hi", doc.conversations.get(0).getMessages().get(0).getContent());
    }
}