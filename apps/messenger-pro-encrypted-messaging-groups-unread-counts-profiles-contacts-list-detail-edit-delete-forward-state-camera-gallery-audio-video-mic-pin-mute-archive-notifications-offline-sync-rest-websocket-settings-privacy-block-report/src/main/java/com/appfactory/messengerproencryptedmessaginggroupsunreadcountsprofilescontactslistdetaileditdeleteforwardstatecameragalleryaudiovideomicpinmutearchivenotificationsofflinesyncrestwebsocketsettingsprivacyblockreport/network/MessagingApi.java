package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.network;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;

import java.util.List;

/**
 * Backend contract. Every operation is replaceable: the app ships a
 * deterministic Mock for tests/offline and a REST implementation for real
 * deployments — no production code is wired to a specific endpoint.
 */
public interface MessagingApi {

    /** Thrown for any API failure; {@link #transient_} marks retriable errors. */
    class ApiException extends Exception {
        public final boolean transient_;
        public final int statusCode;

        public ApiException(String message) {
            this(message, false, 0, null);
        }

        public ApiException(String message, boolean transient_, int statusCode) {
            this(message, transient_, statusCode, null);
        }

        public ApiException(String message, boolean transient_, int statusCode, Throwable cause) {
            super(message, cause);
            this.transient_ = transient_;
            this.statusCode = statusCode;
        }
    }

    List<Message> fetchMessages(String conversationId, String beforeMessageId, int limit)
            throws ApiException;

    Message send(Message out) throws ApiException;

    Message edit(String messageId, String newBody) throws ApiException;

    void delete(String messageId) throws ApiException;

    void markRead(String conversationId, String upToMessageId) throws ApiException;

    void react(String messageId, String emoji) throws ApiException;

    void forward(String messageId, String targetConversationId) throws ApiException;

    void report(String ref, String reason) throws ApiException;

    /** Poll fallback for realtime: messages after a wall-clock cursor. */
    List<Message> fetchIncoming(String conversationId, long sinceCreatedAt) throws ApiException;

    boolean isReachable();
}