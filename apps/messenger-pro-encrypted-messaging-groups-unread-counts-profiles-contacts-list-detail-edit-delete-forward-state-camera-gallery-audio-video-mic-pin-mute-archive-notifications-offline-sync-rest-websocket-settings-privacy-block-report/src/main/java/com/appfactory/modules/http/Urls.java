package com.appfactory.modules.http;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Query-string and URL helpers (pure, deterministically testable). */
public final class Urls {

    private Urls() { }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static String buildQuery(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(encode(e.getKey())).append('=').append(encode(e.getValue()));
        }
        return sb.toString();
    }

    public static String appendQuery(String url, Map<String, String> params) {
        if (params == null || params.isEmpty()) return url;
        int qi = url.indexOf('?');
        if (qi < 0) return url + "?" + buildQuery(params);
        Map<String, String> merged = params(url.substring(qi + 1));
        merged.putAll(params); // new values win; never duplicate existing keys
        return url.substring(0, qi) + "?" + buildQuery(merged);
    }

    /** Validate origin-like URL and forbid non-https unless explicitly allowed. */
    public static void requireSecure(String url) throws IllegalArgumentException {
        if (url == null || url.isEmpty()) throw new IllegalArgumentException("empty url");
        String low = url.toLowerCase();
        if (!low.startsWith("https://")) {
            throw new IllegalArgumentException("TLS required: " + url);
        }
    }

    public static Map<String, String> params(String s) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String pair : s.split("&")) {
            if (pair.isEmpty()) continue;
            String[] kv = pair.split("=", 2);
            String k = kv[0];
            String v = kv.length > 1 ? kv[1] : "";
            m.put(k, v.replace("%20", " "));
        }
        return m;
    }
}