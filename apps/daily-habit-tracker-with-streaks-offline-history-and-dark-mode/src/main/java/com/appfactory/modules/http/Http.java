package com.appfactory.modules.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Minimal REST client over HttpURLConnection. TLS is enforced at the URL layer
 * (see Urls.requireSecure). Never bypasses certificate validation.
 */
public final class Http {

    public static final class Request {
        public final String method;
        public final String url;
        public final Map<String, String> headers = new LinkedHashMap<>();
        public final String body;
        public final int timeoutMs;

        public Request(String method, String url, String body, int timeoutMs) {
            this.method = method;
            this.url = url;
            this.body = body;
            this.timeoutMs = timeoutMs;
        }
        public Request header(String k, String v) {
            headers.put(k, v);
            return this;
        }
        public Request json(String payload) {
            headers.put("Content-Type", "application/json");
            headers.put("Accept", "application/json");
            return this;
        }
    }

    public static final class Response {
        public final int status;
        public final String body;
        public final Map<String, String> headers = new LinkedHashMap<>();

        public Response(int status, String body) { this.status = status; this.body = body; }
        public boolean ok() { return status >= 200 && status < 300; }
    }

    private Http() { }

    public static Response execute(Request req) throws IOException {
        Urls.requireSecure(req.url);
        HttpURLConnection conn = (HttpURLConnection) new URL(req.url).openConnection();
        try {
            conn.setRequestMethod(req.method);
            conn.setConnectTimeout(req.timeoutMs);
            conn.setReadTimeout(req.timeoutMs);
            conn.setInstanceFollowRedirects(true);
            for (Map.Entry<String, String> h : req.headers.entrySet()) {
                conn.setRequestProperty(h.getKey(), h.getValue());
            }
            if (req.body != null) {
                conn.setDoOutput(true);
                byte[] payload = req.body.getBytes(StandardCharsets.UTF_8);
                conn.setRequestProperty("Content-Length", String.valueOf(payload.length));
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }
            }
            int status = conn.getResponseCode();
            Response resp = new Response(status, "");
            for (Map.Entry<String, java.util.List<String>> e : conn.getHeaderFields().entrySet()) {
                if (e.getKey() != null && !e.getValue().isEmpty()) {
                    resp.headers.put(e.getKey(), e.getValue().get(0));
                }
            }
            InputStream raw = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String body = raw == null ? "" : read(raw, "gzip".equalsIgnoreCase(conn.getContentEncoding()));
            return new Response(status, body);
        } finally {
            conn.disconnect();
        }
    }

    private static String read(InputStream in, boolean gzip) throws IOException {
        if (gzip) in = new GZIPInputStream(in);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}