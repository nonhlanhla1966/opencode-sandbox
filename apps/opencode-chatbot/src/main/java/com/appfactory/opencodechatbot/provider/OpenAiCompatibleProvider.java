package com.appfactory.opencodechatbot.provider;

import com.appfactory.opencodechatbot.model.Message;
import com.appfactory.opencodechatbot.util.Json;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenAI-compatible chat provider over {@link HttpURLConnection} — no external
 * networking library. Supports both streaming (SSE) and non-streaming modes.
 * Cancellable via {@link #cancel()}.
 */
public final class OpenAiCompatibleProvider implements ChatProvider {

    private final ProviderConfig config;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile HttpURLConnection connection;
    private final StringBuilder streamed = new StringBuilder();

    public OpenAiCompatibleProvider(ProviderConfig config) {
        this.config = config;
    }

    public void cancel() {
        cancelled.set(true);
        HttpURLConnection c = connection;
        if (c != null) {
            c.disconnect();
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public void send(List<Message> messages, String systemPrompt, boolean stream, Listener listener) {
        if (!config.isConfigured()) {
            listener.onError(new ChatException("Provider is not configured. Open Settings."));
            return;
        }
        String url = config.chatCompletionsUrl();
        if (url.isEmpty()) {
            listener.onError(new ChatException("Invalid endpoint URL."));
            return;
        }
        streamed.setLength(0);

        HttpURLConnection http;
        try {
            http = (HttpURLConnection) new URL(url).openConnection();
            connection = http;
            http.setRequestMethod("POST");
            http.setConnectTimeout(30000);
            http.setReadTimeout(stream ? 180000 : 120000);
            http.setInstanceFollowRedirects(true);
            http.setDoOutput(true);
            http.setRequestProperty("Content-Type", "application/json");
            http.setRequestProperty("Accept", stream ? "text/event-stream" : "application/json");
            if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
                http.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
            }

            Map<String, Object> body = ChatCompletionsRequest.buildBody(config, messages, systemPrompt, stream);
            byte[] payload = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
            http.setFixedLengthStreamingMode(payload.length);
            DataOutputStream out = new DataOutputStream(http.getOutputStream());
            try {
                out.write(payload);
            } finally {
                out.close();
            }

            int code = http.getResponseCode();
            if (code < 200 || code >= 300) {
                String errorBody = readErrorBody(http);
                listener.onError(new ChatException(describeHttpError(code, errorBody)));
                http.disconnect();
                return;
            }

            if (stream) {
                readStreaming(http, listener);
            } else {
                String response = readAll(http.getInputStream());
                String content = SseParser.extractFinalContent(response);
                if (content.isEmpty()) {
                    listener.onError(new ChatException("The provider returned an empty response."));
                } else {
                    listener.onDelta(content);
                    listener.onComplete(content);
                }
            }
        } catch (ChatException e) {
            listener.onError(e);
        } catch (Exception e) {
            if (cancelled.get()) {
                listener.onComplete(streamed.toString());
                return;
            }
            listener.onError(new ChatException(
                    e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException
                            ? "Network error. Check your connection and endpoint."
                            : "Request failed: " + safeMessage(e)));
        } finally {
            HttpURLConnection c = connection;
            if (c != null) {
                c.disconnect();
            }
            connection = null;
        }
    }

    private final StringBuilder partialBuffer = new StringBuilder();

    private void readStreaming(HttpURLConnection http, Listener listener) throws Exception {
        InputStream in = http.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String firstLine = reader.readLine();
        if (firstLine == null) {
            listener.onError(new ChatException("The provider returned an empty response."));
            return;
        }
        // Some OpenAI-compatible endpoints ignore "stream" and send a normal
        // JSON body; detect and fall back gracefully.
        if (firstLine.trim().startsWith("{")) {
            StringBuilder jsonBody = new StringBuilder(firstLine);
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBody.append('\n').append(line);
            }
            String content = SseParser.extractFinalContent(jsonBody.toString());
            if (content.isEmpty()) {
                listener.onError(new ChatException("The provider returned an empty response."));
            } else {
                streamed.append(content);
                listener.onDelta(content);
                listener.onComplete(content);
            }
            return;
        }
        String line = firstLine;
        while (line != null) {
            if (cancelled.get()) {
                break;
            }
            SseParser.Fragment fragment = SseParser.parseLine(line);
            if (fragment != null) {
                if (!fragment.error.isEmpty()) {
                    listener.onError(new ChatException("API error: " + fragment.error));
                    return;
                }
                if (fragment.done) {
                    break;
                }
                if (!fragment.text.isEmpty()) {
                    streamed.append(fragment.text);
                    listener.onDelta(fragment.text);
                }
            }
            line = reader.readLine();
        }
        String text = streamed.toString();
        listener.onComplete(text == null ? "" : text);
    }

    private static String readAll(InputStream in) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static String readErrorBody(HttpURLConnection http) {
        InputStream err = http.getErrorStream();
        if (err == null) {
            return "";
        }
        try {
            InputStreamReader isr = new InputStreamReader(err, StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String raw = sb.toString();
            // Prefer the provider's structured message when it is JSON.
            try {
                Object parsed = Json.parse(raw);
                if (parsed instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> obj = (Map<String, Object>) parsed;
                    String msg = SseParser.extractError(obj);
                    if (!msg.isEmpty()) {
                        return msg;
                    }
                }
            } catch (RuntimeException ignored) {
                // fall through to raw text
            }
            return raw;
        } catch (Exception e) {
            return "";
        }
    }

    private static String describeHttpError(int code, String body) {
        String detail = body == null || body.trim().isEmpty() ? "" : " " + body.trim();
        if (detail.length() > 300) {
            detail = detail.substring(0, 300) + "…";
        }
        return "HTTP " + code + detail;
    }

    private static String safeMessage(Exception e) {
        String msg = e.getMessage();
        return msg == null || msg.isEmpty() ? e.getClass().getSimpleName() : msg;
    }
}