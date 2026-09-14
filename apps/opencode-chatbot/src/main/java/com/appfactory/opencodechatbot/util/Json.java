package com.appfactory.opencodechatbot.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON encoder + parser in pure Java (no Android / org.json
 * dependency), so request construction and conversation serialization are
 * fully unit-testable on the JVM.
 *
 * <p>Parsed values map to: {@link Boolean}, {@link Long} / {@link Double},
 * {@link String}, {@link java.util.List}, {@link java.util.LinkedHashMap},
 * or {@code null}.
 */
public final class Json {

    private Json() {
    }

    // ---------------------------------------------------------------- encode

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(value, sb);
        return sb.toString();
    }

    private static void writeValue(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString((String) value, sb);
        } else if (value instanceof Boolean || value instanceof Long || value instanceof Integer) {
            sb.append(value);
        } else if (value instanceof Double || value instanceof Float) {
            writeNumber(((Number) value).doubleValue(), sb);
        } else if (value instanceof Map) {
            writeObject((Map<?, ?>) value, sb);
        } else if (value instanceof Iterable) {
            writeArray((Iterable<?>) value, sb);
        } else {
            writeString(String.valueOf(value), sb);
        }
    }

    private static void writeNumber(double d, StringBuilder sb) {
        if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
            sb.append((long) d);
        } else {
            sb.append(d);
        }
    }

    private static void writeObject(Map<?, ?> map, StringBuilder sb) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(String.valueOf(e.getKey()), sb);
            sb.append(':');
            writeValue(e.getValue(), sb);
        }
        sb.append('}');
    }

    private static void writeArray(Iterable<?> list, StringBuilder sb) {
        sb.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeValue(item, sb);
        }
        sb.append(']');
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ---------------------------------------------------------------- parse

    public static Object parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("null json");
        }
        Parser p = new Parser(text);
        Object value = p.parseValue();
        p.skipWs();
        if (p.pos < p.text.length()) {
            throw new IllegalArgumentException("Trailing characters at position " + p.pos);
        }
        return value;
    }

    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("Expected JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) v;
        return map;
    }

    private static final class Parser {
        final String text;
        int pos;

        Parser(String text) {
            this.text = text;
        }

        void skipWs() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        char peek() {
            return pos < text.length() ? text.charAt(pos) : '\0';
        }

        char next() {
            if (pos >= text.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            return text.charAt(pos++);
        }

        void expect(char c) {
            skipWs();
            if (peek() != c) {
                throw new IllegalArgumentException("Expected '" + c + "' at position " + pos);
            }
            pos++;
        }

        Object parseValue() {
            skipWs();
            char c = peek();
            switch (c) {
                case '{':
                    return parseObjectImpl();
                case '[':
                    return parseArrayImpl();
                case '"':
                    return parseString();
                case 't':
                    expectLiteral("true");
                    return Boolean.TRUE;
                case 'f':
                    expectLiteral("false");
                    return Boolean.FALSE;
                case 'n':
                    expectLiteral("null");
                    return null;
                default:
                    return parseNumber();
            }
        }

        void expectLiteral(String lit) {
            if (!text.startsWith(lit, pos)) {
                throw new IllegalArgumentException("Invalid literal at position " + pos);
            }
            pos += lit.length();
        }

        Map<String, Object> parseObjectImpl() {
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            expect('{');
            skipWs();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                Object val = parseValue();
                map.put(key, val);
                skipWs();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == '}') {
                    pos++;
                    return map;
                } else {
                    throw new IllegalArgumentException("Expected ',' or '}' at position " + pos);
                }
            }
        }

        List<Object> parseArrayImpl() {
            List<Object> list = new ArrayList<>();
            skipWs();
            expect('[');
            skipWs();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWs();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == ']') {
                    pos++;
                    return list;
                } else {
                    throw new IllegalArgumentException("Expected ',' or ']' at position " + pos);
                }
            }
        }

        String parseString() {
            skipWs();
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char esc = next();
                    switch (esc) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'u':
                            int cp = 0;
                            for (int i = 0; i < 4; i++) {
                                char h = next();
                                cp = (cp << 4) + hexValue(h);
                            }
                            sb.append((char) cp);
                            break;
                        default:
                            throw new IllegalArgumentException("Bad escape '\\" + esc + "' at position " + pos);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        static int hexValue(char c) {
            if (c >= '0' && c <= '9') {
                return c - '0';
            }
            if (c >= 'a' && c <= 'f') {
                return c - 'a' + 10;
            }
            if (c >= 'A' && c <= 'F') {
                return c - 'A' + 10;
            }
            throw new IllegalArgumentException("Bad hex digit '" + c + "'");
        }

        Object parseNumber() {
            int start = pos;
            skipWs();
            if (peek() == '-') {
                pos++;
            }
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    pos++;
                } else {
                    break;
                }
            }
            String num = text.substring(start, pos);
            if (num.isEmpty() || num.equals("-")) {
                throw new IllegalArgumentException("Invalid number at position " + start);
            }
            if (num.contains(".") || num.contains("e") || num.contains("E")) {
                try {
                    return Double.parseDouble(num);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid number '" + num + "'");
                }
            }
            try {
                return Long.parseLong(num);
            } catch (NumberFormatException e) {
                try {
                    return Double.parseDouble(num);
                } catch (NumberFormatException e2) {
                    throw new IllegalArgumentException("Invalid number '" + num + "'");
                }
            }
        }
    }
}