package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.network;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;
import com.appfactory.modules.http.Http;
import com.appfactory.modules.retry.Retry;

import java.io.IOException;
import java.util.List;

/**
 * REST implementation of {@link MessagingApi} over HttpURLConnection.
 * Uses the vendored Http client (TLS-only via Urls.requireSecure), retries
 * transient I/O errors with bounded backoff, never bypasses TLS.
 */
public final class RestMessagingApi implements MessagingApi {

    private final RestContract contract;
    private final int maxAttempts;
    private final long baseMs;

    public RestMessagingApi(RestContract contract) {
        this(contract, 3, 300L);
    }

    public RestMessagingApi(RestContract contract, int maxAttempts, long baseMs) {
        this.contract = contract;
        this.maxAttempts = maxAttempts;
        this.baseMs = baseMs;
    }

    private <T> T withRetry(Retry.Action<T> action) throws ApiException {
        try {
            return Retry.run(action, maxAttempts, baseMs, Retry.RETRY_IO);
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException("network error: " + e.getMessage(), true, 0, e);
        } catch (Exception e) {
            throw new ApiException("request failed", false, 0, e);
        }
    }

    private void require(Http.Response r) throws ApiException {
        if (!r.ok()) {
            boolean transient_ = r.status >= 500;
            throw new ApiException(RestContract.parseError(r.body), transient_, r.status);
        }
    }

    @Override
    public List<Message> fetchMessages(String conversationId, String beforeMessageId, int limit)
            throws ApiException {
        return withRetry(() -> {
            Http.Response r = Http.execute(
                    contract.fetchMessagesRequest(conversationId, beforeMessageId, limit));
            require(r);
            return RestContract.parseMessages(r.body);
        });
    }

    @Override
    public Message send(Message out) throws ApiException {
        return withRetry(() -> {
            Http.Response r = Http.execute(contract.sendRequest(out));
            require(r);
            return RestContract.parseMessage(r.body);
        });
    }

    @Override
    public Message edit(String messageId, String newBody) throws ApiException {
        return withRetry(() -> {
            Http.Response r = Http.execute(contract.editRequest(messageId, newBody));
            require(r);
            return RestContract.parseMessage(r.body);
        });
    }

    @Override
    public void delete(String messageId) throws ApiException {
        withRetry(() -> {
            Http.Response r = Http.execute(contract.deleteRequest(messageId));
            require(r);
            return null;
        });
    }

    @Override
    public void markRead(String conversationId, String upToMessageId) throws ApiException {
        withRetry(() -> {
            Http.Response r = Http.execute(contract.markReadRequest(conversationId, upToMessageId));
            require(r);
            return null;
        });
    }

    @Override
    public void react(String messageId, String emoji) throws ApiException {
        withRetry(() -> {
            Http.Response r = Http.execute(contract.reactRequest(messageId, emoji));
            require(r);
            return null;
        });
    }

    @Override
    public void forward(String messageId, String targetConversationId) throws ApiException {
        withRetry(() -> {
            Http.Response r = Http.execute(contract.forwardRequest(messageId, targetConversationId));
            require(r);
            return null;
        });
    }

    @Override
    public void report(String ref, String reason) throws ApiException {
        withRetry(() -> {
            Http.Response r = Http.execute(contract.reportRequest(ref, reason));
            if (r.status >= 300) {
                throw new ApiException(RestContract.parseError(r.body), false, r.status);
            }
            return null;
        });
    }

    @Override
    public List<Message> fetchIncoming(String conversationId, long sinceCreatedAt)
            throws ApiException {
        return withRetry(() -> {
            Http.Response r = Http.execute(contract.incomingRequest(conversationId, sinceCreatedAt));
            require(r);
            return RestContract.parseMessages(r.body);
        });
    }

    @Override
    public boolean isReachable() {
        try {
            Http.Response r = Http.execute(contract.healthRequest());
            return r.ok() || r.status == 401 || r.status == 403;
        } catch (IOException e) {
            return false;
        }
    }
}