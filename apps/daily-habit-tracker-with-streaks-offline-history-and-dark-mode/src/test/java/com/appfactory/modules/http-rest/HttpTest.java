package com.appfactory.modules.http-rest;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public class HttpTest {
    @Test public void queryBuilding() {
        Map<String, String> q = new java.util.LinkedHashMap<>();
        q.put("a", "1");
        q.put("q", "hello world");
        assertEquals("a=1&q=hello%20world", Urls.buildQuery(q));
        assertEquals("https://x.test/?a=1&q=hello%20world",
                Urls.appendQuery("https://x.test/", q));
        assertEquals("https://x.test/p?a=1&q=hello%20world",
                Urls.appendQuery("https://x.test/p?a=1", q));
    }
    @Test public void tlsEnforced() {
        try {
            Urls.requireSecure("http://insecure.example/x");
            fail("http must be rejected");
        } catch (IllegalArgumentException expected) { }
        Urls.requireSecure("https://ok.example/x"); // no throw
    }
    @Test public void paramsRoundTrip() {
        Map<String, String> p = Urls.params("a=1&q=hello%20world");
        assertEquals("1", p.get("a"));
        assertEquals("hello world", p.get("q"));
    }
    @Test public void requestJsonHeaders() {
        Http.Request req = new Http.Request("POST", "https://api.example/chat",
                "{\"x\":1}", 1000).json("{\"x\":1}");
        assertEquals("POST", req.method);
        assertEquals("application/json", req.headers.get("Content-Type"));
    }
}