package com.appfactory.modules.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, strict JSON parser/serializer (no reflection, no framework deps).
 * Integers parse to Long, decimals to Double, plus Boolean, String, null,
 * nested JsonObject / JsonArray. Serialization always produces compact JSON.
 */
public final class Json {

    private Json() { }

    public static JsonObject parseObject(String text) {
        Parser p = new Parser(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (!(v instanceof JsonObject)) {
            throw new JsonException("expected JSON object");
        }
        return (JsonObject) v;
    }

    public static String quote(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    static String render(Object v) {
        if (v == null) return "null";
        if (v instanceof JsonObject) return ((JsonObject) v).build();
        if (v instanceof JsonArray) return ((JsonArray) v).build();
        if (v instanceof String) return quote((String) v);
        if (v instanceof Boolean) return ((Boolean) v) ? "true" : "false";
        if (v instanceof Long || v instanceof Integer) return v.toString();
        if (v instanceof Double) {
            double d = (Double) v;
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                long l = (long) d;
                return l + ".0";
            }
            return Double.toString(d);
        }
        if (v instanceof Number) return v.toString();
        return quote(String.valueOf(v));
    }

    /** Strict parser, recursive descent. */
    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s == null ? "" : s;
        }

        void ws() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
                else break;
            }
        }

        Object value() {
            ws();
            if (i >= s.length()) throw new JsonException("unexpected end of input");
            char c = s.charAt(i);
            switch (c) {
                case '{': return object();
                case '[': return array();
                case '"': return str();
                case 't': expect("true"); return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null"); return null;
                default: return number();
            }
        }

        JsonObject object() {
            i++; // {
            JsonObject o = new JsonObject();
            ws();
            if (peek() == '}') { i++; return o; }
            while (true) {
                String key = str();
                ws();
                if (peek() != ':') throw new JsonException("expected ':'");
                i++;
                Object v = value();
                o.raw.put(key, v);
                ws();
                char c = next();
                if (c == ',') continue;
                if (c == '}') return o;
                throw new JsonException("expected ',' or '}'");
            }
        }

        JsonArray array() {
            i++; // [
            JsonArray a = new JsonArray();
            ws();
            if (peek() == ']') { i++; return a; }
            while (true) {
                a.raw.add(value());
                ws();
                char c = next();
                if (c == ',') continue;
                if (c == ']') return a;
                throw new JsonException("expected ',' or ']'");
            }
        }

        String str() {
            if (peek() != '"') throw new JsonException("expected string");
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            if (i + 4 > s.length()) throw new JsonException("bad \\u escape");
                            String hex = s.substring(i, i + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                            break;
                        default: throw new JsonException("bad escape \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new JsonException("unterminated string");
        }

        Object number() {
            int start = i;
            if (peek() == '-') i++;
            while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            boolean isDouble = false;
            if (i < s.length() && s.charAt(i) == '.') {
                isDouble = true;
                i++;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                isDouble = true;
                i++;
                if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            }
            String num = s.substring(start, i);
            if (num.isEmpty() || num.equals("-")) throw new JsonException("bad number");
            try {
                if (isDouble) return Double.parseDouble(num);
                return Long.parseLong(num);
            } catch (NumberFormatException nfe) {
                throw new JsonException("bad number: " + num);
            }
        }

        char peek() {
            return i < s.length() ? s.charAt(i) : '\0';
        }

        char next() {
            if (i >= s.length()) throw new JsonException("unexpected end");
            return s.charAt(i++);
        }

        void expect(String tok) {
            if (!s.startsWith(tok, i)) throw new JsonException("expected " + tok);
            i += tok.length();
        }
    }
}