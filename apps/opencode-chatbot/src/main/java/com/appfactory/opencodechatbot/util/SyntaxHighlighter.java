package com.appfactory.opencodechatbot.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Minimal language-aware syntax tokenizer that finds the spans of a code
 * block that should be highlighted (keywords, strings, comments, numbers,
 * types). Pure Java — testable. Output tokens are offsets into the source.
 */
public final class SyntaxHighlighter {

    public static final int KIND_KEYWORD = 1;
    public static final int KIND_STRING = 2;
    public static final int KIND_COMMENT = 3;
    public static final int KIND_NUMBER = 4;
    public static final int KIND_TYPE = 5;

    public static final class Token {
        public final int start;
        public final int end;
        public final int kind;

        Token(int start, int end, int kind) {
            this.start = start;
            this.end = end;
            this.kind = kind;
        }
    }

    private static final Set<String> JAVA_KEYWORDS = new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false",
            "null"));

    private static final Set<String> JAVA_TYPES = new HashSet<>(Arrays.asList(
            "String", "Integer", "Long", "Double", "Float", "Boolean", "Short", "Byte", "Character",
            "Object", "List", "Map", "Set", "ArrayList", "HashMap", "HashSet", "Exception",
            "RuntimeException", "Thread", "Runnable", "StringBuilder"));

    private static final Set<String> PY_KEYWORDS = new HashSet<>(Arrays.asList(
            "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del",
            "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in",
            "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while",
            "with", "yield", "True", "False", "None", "self"));

    private static final Set<String> JS_KEYWORDS = new HashSet<>(Arrays.asList(
            "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger",
            "default", "delete", "do", "else", "export", "extends", "finally", "for", "from",
            "function", "get", "if", "import", "in", "instanceof", "let", "new", "of", "return",
            "set", "static", "super", "switch", "this", "throw", "try", "typeof", "var", "void",
            "while", "with", "yield", "true", "false", "null", "undefined"));

    private static final Set<String> SHELL_KEYWORDS = new HashSet<>(Arrays.asList(
            "if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done", "case",
            "esac", "function", "in", "export", "local", "return", "exit", "echo", "source",
            "cd", "printf", "set", "unset", "true", "false"));

    private static final Set<String> SQL_KEYWORDS = new HashSet<>(Arrays.asList(
            "select", "from", "where", "insert", "into", "values", "update", "set", "delete",
            "create", "table", "drop", "alter", "join", "left", "right", "inner", "outer", "on",
            "as", "and", "or", "not", "null", "group", "by", "order", "having", "limit", "offset",
            "distinct", "between", "like", "in", "union", "all", "case", "when", "then", "else",
            "end", "primary", "key", "foreign", "references", "index", "view", "add", "column",
            "default", "unique", "constraint", "exists"));

    private static final Set<String> C_KEYWORDS = new HashSet<>(Arrays.asList(
            "auto", "break", "case", "const", "continue", "default", "do", "else", "enum", "extern",
            "for", "goto", "if", "inline", "register", "restrict", "return", "short", "signed",
            "sizeof", "static", "struct", "switch", "typedef", "union", "unsigned", "void",
            "volatile", "while", "using", "namespace", "class", "public", "private", "protected",
            "virtual", "override", "new", "delete", "this", "include", "define", "ifdef", "ifndef",
            "endif", "pragma", "int", "long", "float", "double", "char", "bool", "true", "false"));

    private static final Set<String> RUST_KEYWORDS = new HashSet<>(Arrays.asList(
            "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else", "enum",
            "extern", "fn", "for", "if", "impl", "in", "let", "loop", "match", "mod", "move", "mut",
            "pub", "ref", "return", "self", "Self", "static", "struct", "super", "trait", "true",
            "false", "type", "unsafe", "use", "where", "while"));

    // ---------------------------------------------------------------- public

    private SyntaxHighlighter() {
    }

    public static List<Token> highlight(String lang, String code) {
        List<Token> tokens = new ArrayList<>();
        if (code == null || code.isEmpty()) {
            return tokens;
        }
        String normalized = normalizeLang(lang);
        String lower = normalized;
        tokens.addAll(scan(code, lower));
        return tokens;
    }

    private static String normalizeLang(String lang) {
        if (lang == null) {
            return "";
        }
        String l = lang.trim().toLowerCase(Locale.US);
        if (l.contains("+")) {          // c++, c#
            return "c";
        }
        if (l.equals("sh") || l.equals("bash") || l.equals("zsh") || l.equals("shell")) {
            return "shell";
        }
        if (l.equals("js") || l.equals("javascript") || l.equals("ts") || l.equals("typescript") || l.equals("jsx") || l.equals("tsx")) {
            return "js";
        }
        if (l.equals("py") || l.equals("python")) {
            return "python";
        }
        if (l.equals("rs") || l.equals("rust")) {
            return "rust";
        }
        if (l.equals("html") || l.equals("xml") || l.equals("sql")) {
            return l;
        }
        if (l.isEmpty() || l.equals("text") || l.equals("plain")) {
            return "";
        }
        return "java"; // best-effort default for c-family/generic
    }

    private static Set<String> keywordsFor(String lang) {
        switch (lang) {
            case "python":
                return PY_KEYWORDS;
            case "js":
                return JS_KEYWORDS;
            case "shell":
                return SHELL_KEYWORDS;
            case "sql":
                return SQL_KEYWORDS;
            case "rust":
                return RUST_KEYWORDS;
            case "c":
            case "html":
            case "xml":
                return C_KEYWORDS;
            case "java":
                return JAVA_KEYWORDS;
            default:
                return JAVA_KEYWORDS;
        }
    }

    private static List<Token> scan(String code, String lang) {
        List<Token> tokens = new ArrayList<>();
        Set<String> keywords = keywordsFor(lang);
        int i = 0;
        int n = code.length();
        while (i < n) {
            char c = code.charAt(i);

            if (c == '/' && i + 1 < n && code.charAt(i + 1) == '/') {
                int end = code.indexOf('\n', i);
                end = (end == -1) ? n : end;
                tokens.add(new Token(i, end, KIND_COMMENT));
                i = end;
                continue;
            }
            if (c == '#') {
                int end = code.indexOf('\n', i);
                end = (end == -1) ? n : end;
                tokens.add(new Token(i, end, KIND_COMMENT));
                i = end;
                continue;
            }
            if (c == ';' && lang.equals("sql")) {
                int end = code.indexOf('\n', i);
                end = (end == -1) ? n : end;
                tokens.add(new Token(i, end, KIND_COMMENT));
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < n && code.charAt(i + 1) == '*') {
                int end = code.indexOf("*/", i + 2);
                end = (end == -1) ? n : end + 2;
                tokens.add(new Token(i, end, KIND_COMMENT));
                i = end;
                continue;
            }
            if ((lang.equals("html") || lang.equals("xml"))
                    && c == '<' && i + 3 < n && code.startsWith("<!--", i)) {
                int end = code.indexOf("-->", i + 4);
                end = (end == -1) ? n : end + 3;
                tokens.add(new Token(i, end, KIND_COMMENT));
                i = end;
                continue;
            }
            if (c == '-' && i + 1 < n && code.charAt(i + 1) == '-') {
                int end = code.indexOf('\n', i);
                end = (end == -1) ? n : end;
                tokens.add(new Token(i, end, KIND_COMMENT));
                i = end;
                continue;
            }

            if (c == '"' || c == '\'' || c == '`') {
                int end = scanString(code, i, c);
                tokens.add(new Token(i, end, KIND_STRING));
                i = end;
                continue;
            }

            if (Character.isDigit(c)) {
                int end = i;
                while (end < n && (Character.isDigit(code.charAt(end))
                        || "xXaAbBcCdDeEfF._-".indexOf(code.charAt(end)) >= 0
                        || (end > i && (code.charAt(end) == '+' || code.charAt(end) == '-')))) {
                    end++;
                }
                tokens.add(new Token(i, end, KIND_NUMBER));
                i = end;
                continue;
            }

            if (Character.isLetter(c) || c == '_') {
                int end = i;
                while (end < n && (Character.isLetterOrDigit(code.charAt(end)) || code.charAt(end) == '_')) {
                    end++;
                }
                String word = code.substring(i, end);
                if (keywords.contains(word)) {
                    tokens.add(new Token(i, end, KIND_KEYWORD));
                } else if (Character.isUpperCase(c)
                        && (lang.equals("java") || lang.equals("c") || lang.equals("rust"))
                        && JAVA_TYPES.contains(word)) {
                    tokens.add(new Token(i, end, KIND_TYPE));
                }
                i = end;
                continue;
            }

            i++;
        }
        return tokens;
    }

    private static int scanString(String code, int start, char quote) {
        int i = start + 1;
        int n = code.length();
        while (i < n) {
            char c = code.charAt(i);
            if (c == '\\' && i + 1 < n) {
                i += 2;
                continue;
            }
            if (c == quote) {
                return i + 1;
            }
            if (c == '\n') {
                return i; // unterminated; end at newline
            }
            i++;
        }
        return n;
    }
}