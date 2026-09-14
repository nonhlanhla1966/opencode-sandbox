package com.appfactory.opencodechatbot.provider;

import com.appfactory.opencodechatbot.model.Message;

import java.util.List;

/** Streaming chat provider abstraction — one implementation per API family. */
public interface ChatProvider {

    /**
     * Execute a chat request. When {@code stream} is true the implementation
     * delivers deltas through {@code listener} incrementally and calls
     * {@code onComplete} exactly once (also on empty content). When false it
     * delivers the full content in a single {@code onDelta} call.
     *
     * @throws ChatException with a user-presentable message on any failure.
     */
    void send(List<Message> messages, String systemPrompt, boolean stream, Listener listener);

    interface Listener {
        void onDelta(String text);

        void onComplete(String fullText);

        void onError(ChatException e);
    }
}