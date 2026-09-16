package com.appfactory.modules.json;

import org.junit.Test;

import static org.junit.Assert.*;

public class JsonTest {
    @Test public void parsesNestedObject() {
        JsonObject o = Json.parseObject("{\"a\":{\"b\":[1,2,3],\"c\":\"x\"},\"n\":null}");
        assertEquals(2, o.getObject("a").getArray("b").getInt(1, -1));
        assertEquals("x", o.getObject("a").getString("c"));
        assertTrue(o.has("n"));
    }
    @Test public void serializesAndRoundTrips() {
        JsonObject a = new JsonObject();
        a.put("title", "hello").put("count", 3).put("ratio", 1.5);
        JsonArray arr = new JsonArray();
        arr.add(new JsonObject().put("k", "v"));
        a.put("list", arr);
        String text = a.toString();
        assertTrue(text.contains("\"title\":\"hello\""));
        JsonObject b = Json.parseObject(text);
        assertEquals("hello", b.getString("title"));
        assertEquals(3, b.getInt("count", -1));
        assertEquals(1.5, b.getDouble("ratio", -1), 0.0001);
        assertEquals("v", b.getArray("list").getObject(0).getString("k"));
    }
    @Test public void escapesStringsSafely() {
        assertTrue(Json.quote("a\"b\\c\nd").startsWith("\"a\\\"b\\\\c\\nd\""));
    }
    @Test public void handlesUnicodeAndNumbers() {
        JsonObject o = Json.parseObject("{\"u\":\"caf\\u00e9\",\"big\":1234567890123}");
        assertEquals("café", o.getString("u"));
        assertEquals(1234567890123L, o.getLong("big", -1));
    }
    @Test(expected = JsonException.class) public void rejectsBadJson() {
        Json.parseObject("{bad}");
    }
    @Test public void emptyObjectAndArray() {
        assertEquals(0, Json.parseObject("{}").size());
        assertEquals(0, Json.parseObject("{\"a\":[]}").getArray("a").size());
    }
}