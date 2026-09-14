package com.appfactory.opencodechatbot.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight Markdown parser producing a structured document that a renderer
 * (e.g. {@code MarkdownSpanner}) turns into styled spans. Pure Java — fully
 * unit-testable, no Android dependencies.
 *
 * <p>Supported: ATX headings, fenced code blocks with language tag, paragraphs,
 * blockquotes, bullet/ordered lists, thematic breaks, and inline
 * bold/italic/strikethrough/inline-code/links.
 */
public final class Markdown {

    // Inline styles
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int STYLE_STRIKE = 4;

    // Block kinds
    public static final int BLOCK_HEADING = 1;
    public static final int BLOCK_PARAGRAPH = 2;
    public static final int BLOCK_CODE = 3;
    public static final int BLOCK_QUOTE = 4;
    public static final int BLOCK_LIST_ITEM = 5;
    public static final int BLOCK_HR = 6;

    /** A parsed inline element. */
    public static final class Inline {
        public final int style;
        public final String text;
        public final String url;

        Inline(int style, String text, String url) {
            this.style = style;
            this.text = text;
            this.url = url;
        }

        public static Inline text(String t) {
            return new Inline(0, t, null);
        }

        public static Inline code(String t) {
            return new Inline(STYLE_CODE_OUT, t, null);
        }

        public static Inline link(String label, String url) {
            return new Inline(STYLE_LINK, label, url);
        }
    }

    public static final int STYLE_CODE_OUT = 8;
    public static final int STYLE_LINK = 16;

    /** A parsed block. For CODE, {@link #text} is the raw code, {@link #url} the language. */
    public static final class Block {
        public final int kind;
        public final int level;
        public final List<Inline> inlines;
        public final String text;
        public final String url;
        public final boolean ordered;

        Block(int kind, int level, List<Inline> inlines, String text, String url, boolean ordered) {
            this.kind = kind;
            this.level = level;
            this.inlines = inlines;
            this.text = text;
            this.url = url;
            this.ordered = ordered;
        }
    }

    public static final class Document {
        public final List<Block> blocks;

        Document(List<Block> blocks) {
            this.blocks = blocks;
        }

        public List<Block> getBlocks() {
            return blocks;
        }
    }

    private Markdown() {
    }

    public static Document parse(String source) {
        List<Block> blocks = new ArrayList<>();
        if (source == null) {
            return new Document(blocks);
        }
        String[] rawLines = source.split("\n", -1);
        List<String> lines = new ArrayList<>();
        for (String l : rawLines) {
            lines.add(l);
        }

        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                i++;
                continue;
            }

            // fenced code block
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                String fence = trimmed.substring(0, 3);
                String lang = trimmed.length() > 3 ? trimmed.substring(3).trim() : "";
                StringBuilder code = new StringBuilder();
                i++;
                while (i < lines.size()) {
                    String cl = lines.get(i);
                    String ct = cl.trim();
                    if (ct.startsWith(fence)) {
                        i++;
                        break;
                    }
                    code.append(cl).append('\n');
                    i++;
                }
                blocks.add(new Block(BLOCK_CODE, 0, null, code.toString(), lang, false));
                continue;
            }

            // ATX heading
            if (trimmed.startsWith("#")) {
                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#') {
                    level++;
                }
                if (level <= 6 && (trimmed.length() == level || Character.isWhitespace(trimmed.charAt(level)))) {
                    String text = trimmed.substring(level).trim();
                    blocks.add(new Block(BLOCK_HEADING, level,
                            parseInlines(cleanHeading(text)), null, null, false));
                    i++;
                    continue;
                }
            }

            // thematic break
            if (isThematicBreak(trimmed)) {
                blocks.add(new Block(BLOCK_HR, 0, null, null, null, false));
                i++;
                continue;
            }

            // blockquote
            if (trimmed.startsWith(">")) {
                String q = trimmed.substring(1).trim();
                blocks.add(new Block(BLOCK_QUOTE, 0, parseInlines(q), null, null, false));
                i++;
                continue;
            }

            // list item
            String listMarker = listMarker(trimmed);
            if (listMarker != null) {
                boolean ordered = Character.isDigit(listMarker.charAt(0));
                int contentStart = trimmed.indexOf(listMarker) + listMarker.length();
                String content = trimmed.substring(contentStart).trim();
                blocks.add(new Block(BLOCK_LIST_ITEM, 0, parseInlines(content), null, null, ordered));
                i++;
                continue;
            }

            // paragraph: gather until blank / interruption
            StringBuilder para = new StringBuilder();
            while (i < lines.size()) {
                String pl = lines.get(i);
                String pt = pl.trim();
                if (pt.isEmpty()
                        || pt.startsWith("```") || pt.startsWith("~~~")
                        || pt.startsWith("#")
                        || listMarker(pt) != null
                        || pt.startsWith(">")) {
                    break;
                }
                if (para.length() > 0) {
                    para.append('\n');
                }
                para.append(pl);
                i++;
            }
            blocks.add(new Block(BLOCK_PARAGRAPH, 0, parseInlines(para.toString()), null, null, false));
        }
        return new Document(blocks);
    }

    private static String cleanHeading(String h) {
        String t = h.trim();
        while (t.endsWith("#")) {
            t = t.substring(0, t.length() - 1);
        }
        return t.trim();
    }

    static boolean isThematicBreak(String trimmed) {
        String base = trimmed.replaceAll("\\*", "").replaceAll("-", "")
                .replaceAll("_", "").trim();
        return base.isEmpty() && trimmed.length() >= 3;
    }

    public static String listMarker(String trimmed) {
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
            return trimmed.substring(0, 2);
        }
        int dot = trimmed.indexOf(". ");
        if (dot > 0) {
            String num = trimmed.substring(0, dot);
            if (num.length() <= 3 && num.chars().allMatch(Character::isDigit)) {
                return trimmed.substring(0, dot + 1);
            }
        }
        return null;
    }

    // ------------------------------------------------------------- inline

    static List<Inline> parseInlines(String text) {
        List<Inline> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        parseInlinesInto(text, out, 0, text.length());
        return out;
    }

    private static void parseInlinesInto(String s, List<Inline> out, int from, int to) {
        int i = from;
        StringBuilder plain = new StringBuilder();

        while (i < to) {
            char c = s.charAt(i);

            if (c == '`') {
                int end = s.indexOf('`', i + 1);
                if (end > i + 1 && end < to) {
                    flush(plain, out);
                    out.add(Inline.code(s.substring(i + 1, end)));
                    i = end + 1;
                    continue;
                }
            }

            if (c == '[') {
                int close = indexOfCloseBracket(s, i);
                if (close > i) {
                    int openParen = s.indexOf('(', close);
                    int closeParen = s.indexOf(')', close);
                    if (openParen == close + 1 && closeParen > openParen) {
                        String label = s.substring(i + 1, close);
                        String url = s.substring(openParen + 1, closeParen).trim();
                        flush(plain, out);
                        out.add(Inline.link(parseInlines(label).isEmpty()
                                ? label : inlinePlain(parseInlines(label)), url));
                        i = closeParen + 1;
                        continue;
                    }
                }
            }

            if (c == '*' || c == '_' || c == '~') {
                boolean isStrike = c == '~';
                char marker = c;
                int markerLen = isStrike ? 2 : 1;
                int next = s.indexOf(marker, i + 1);
                if (!isStrike && (marker == '_' || marker == '*')) {
                    // try double first for bold
                    if (i + 1 < to && s.charAt(i + 1) == marker) {
                        int end2 = s.indexOf("" + marker + marker, i + 2);
                        if (end2 > i + 2) {
                            flush(plain, out);
                            out.add(new Inline(STYLE_BOLD,
                                    inlinePlain(parseInlines(s.substring(i + 2, end2))), null));
                            i = end2 + 2;
                            continue;
                        }
                    }
                }
                if (isStrike) {
                    if (i + 1 < to && s.charAt(i + 1) == '~') {
                        int end2 = s.indexOf("~~", i + 2);
                        if (end2 > i + 2) {
                            flush(plain, out);
                            out.add(new Inline(STYLE_STRIKE,
                                    inlinePlain(parseInlines(s.substring(i + 2, end2))), null));
                            i = end2 + 2;
                            continue;
                        }
                    }
                }
                if (next > i + 1 && next < to) {
                    flush(plain, out);
                    out.add(new Inline(STYLE_ITALIC,
                            inlinePlain(parseInlines(s.substring(i + 1, next))), null));
                    i = next + 1;
                    continue;
                }
            }

            plain.append(c);
            i++;
        }
        flush(plain, out);
    }

    private static int indexOfCloseBracket(String s, int start) {
        for (int i = start + 1; i < s.length(); i++) {
            if (s.charAt(i) == ']') {
                return i;
            }
        }
        return -1;
    }

    public static String inlinePlain(List<Inline> inlines) {
        StringBuilder sb = new StringBuilder();
        for (Inline in : inlines) {
            sb.append(in.text);
        }
        return sb.toString();
    }

    private static void flush(StringBuilder plain, List<Inline> out) {
        if (plain.length() > 0) {
            out.add(Inline.text(plain.toString()));
            plain.setLength(0);
        }
    }
}