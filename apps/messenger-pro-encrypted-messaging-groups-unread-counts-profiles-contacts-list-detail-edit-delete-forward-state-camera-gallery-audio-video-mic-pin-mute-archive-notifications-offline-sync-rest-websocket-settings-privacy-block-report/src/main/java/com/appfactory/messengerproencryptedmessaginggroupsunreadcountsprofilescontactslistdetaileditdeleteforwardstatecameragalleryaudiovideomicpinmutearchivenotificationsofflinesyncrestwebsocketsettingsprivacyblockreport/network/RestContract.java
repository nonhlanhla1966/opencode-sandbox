package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.network;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;
import com.appfactory.modules.http.Http;
import com.appfactory.modules.http.Urls;
import com.appfactory.modules.json.Json;
import com.appfactory.modules.json.JsonArray;
import com.appfactory.modules.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure REST/JSON wire contract: builds Http.Request values and parses
 * responses — fully unit-testable without any network (no side effects).
 * The base URL is user-supplied at runtime; plain http:// is rejected.
 */
public final class RestContract {

    public static final int TIMEOUT_MS = 10_000;

    private final String baseUrl;
    private final String authToken;

    public RestContract(String baseUrl, String authToken) {
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        this.authToken = authToken == null ? "" : authToken;
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** Null when valid; error message when the base URL is unusable. */
    public static String validateBase(String url) {
        if (url == null || url.trim().isEmpty()) return "endpoint is empty";
        try {
            Urls.requireSecure(url.trim());
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        return null;
    }

    String url(String path) {
        String joined = baseUrl.endsWith("/")
                ? baseUrl + (path.startsWith("/") ? path.substring(1) : path)
                : baseUrl + "/" + (path.startsWith("/") ? path.substring(1) : path);
        return joined;
    }

    private Http.Request authenticated(String method, String path, String body) {
        Http.Request req = new Http.Request(method, url(path), body, TIMEOUT_MS);
        if (!authToken.isEmpty()) req.header("Authorization", "Bearer " + authToken);
        if (body != null) req.json(body);
        return req;
    }

    // ---- messages ----

    public Http.Request fetchMessagesRequest(String conversationId, String beforeMessageId, int limit) {
        String path = "conversations/" + Urls.encode(conversationId) + "/messages"
                + "?limit=" + limit
                + (beforeMessageId == null ? "" : "&before=" + Urls.encode(beforeMessageId));
        return authenticated("GET", path, null);
    }

    public Http.Request sendRequest(Message m) {
        return authenticated("POST", "conversations/" + Urls.encode(m.conversationId) + "/messages",
                messageToJson(m));
    }

    public Http.Request editRequest(String messageId, String newBody) {
        JsonObject o = new JsonObject().put("newBody", newBody == null ? "" : newBody);
        return authenticated("PATCH", "messages/" + Urls.encode(messageId), o.toString());
    }

    public Http.Request deleteRequest(String messageId) {
        return authenticated("DELETE", "messages/" + Urls.encode(messageId), null);
    }

    public Http.Request markReadRequest(String conversationId, String upToMessageId) {
        JsonObject o = new JsonObject().put("upToMessageId", upToMessageId == null ? "" : upToMessageId);
        return authenticated("POST", "conversations/" + Urls.encode(conversationId) + "/read", o.toString());
    }

    public Http.Request reactRequest(String messageId, String emoji) {
        JsonObject o = new JsonObject().put("emoji", emoji == null ? "" : emoji);
        return authenticated("POST", "messages/" + Urls.encode(messageId) + "/reaction", o.toString());
    }

    public Http.Request forwardRequest(String messageId, String targetConversationId) {
        JsonObject o = new JsonObject().put("targetConversationId", targetConversationId);
        return authenticated("POST", "messages/" + Urls.encode(messageId) + "/forward", o.toString());
    }

    public Http.Request reportRequest(String ref, String reason) {
        JsonObject o = new JsonObject().put("ref", ref == null ? "" : ref)
                .put("reason", reason == null ? "" : reason);
        return authenticated("POST", "reports", o.toString());
    }

    public Http.Request incomingRequest(String conversationId, long sinceCreatedAt) {
        return authenticated("GET", "conversations/" + Urls.encode(conversationId)
                + "/messages?since=" + sinceCreatedAt, null);
    }

    public Http.Request healthRequest() {
        return authenticated("GET", "health", null);
    }

    // ---- parsing ----

    public static String messageToJson(Message m) {
        return m.toJson().toString();
    }

    public static Message parseMessage(String body) {
        if (body == null || body.trim().isEmpty()) {
            throw new IllegalStateException("empty message body");
        }
        return Message.fromJson(Json.parseObject(body));
    }

    public static List<Message> parseMessages(String body) {
        JsonArray arr = Json.parseObject(body).getArray("messages");
        List<Message> out = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            out.add(Message.fromJson(arr.getObject(i)));
        }
        return out;
    }

    /** Extracts a stable error summary from an error body (best effort). */
    public static String parseError(String body) {
        if (body == null || body.trim().isEmpty()) return "empty error response";
        try {
            JsonObject o = Json.parseObject(body);
            return o.getString("error", o.getString("message", "HTTP error"));
        } catch (RuntimeException e) {
            String t = body.trim();
            return t.length() > 120 ? t.substring(0, 120) : t;
        }
    }
}