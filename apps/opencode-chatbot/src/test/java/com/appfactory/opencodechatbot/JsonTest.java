package com.appfactory.opencodechatbot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.appfactory.opencodechatbot.util.Json;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonTest {

    @Test
    public void roundTripsObject() {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("role", "user");
        obj.put("stream", true);
        obj.put("count", 3L);
        obj.put("ratio", 0.5);
        obj.put("empty", null);

        String json = Json.stringify(obj);
        Object parsed = Json.parse(json);
        assertNotNull(parsed);
        assert parsesAreEqual(obj, Json.parseObject(json));
    }

    @Test
    public void escapesStrings() {
        assertEquals("\"a\\\"b\\nc\\t\"", Json.stringify("a\"b\nc\t"));
        Object back = Json.parse(Json.stringify("line1\nline2"));
        assertEquals("line1\nline2", back);
    }

    @Test
    public void reconstructsUnicodeEscape() {
        assertEquals("café", Json.parse("\"caf\\u00e9\""));
    }

    @Test
    public void parsesNestedArrays() {
        List<Object> list = Arrays.asList("a", 1L, true, null);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("items", list);
        String json = Json.stringify(root);
        Map<String, Object> parsed = Json.parseObject(json);
        assertEquals(list, parsed.get("items"));
    }

    @Test
    public void rejectsTrailingGarbage() {
        try {
            Json.parse("{\"a\":1} extra");
            fail("should throw");
        } catch (RuntimeException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void parsesLeadingWhitespace() {
        Object v = Json.parse("   {\"ok\":true}   ");
        assertNotNull(v);
    }

    private static Object parsesAreEqual(Object expected, Object actual) {
        assertEquals(expected, actual);
        return actual;
    }
}