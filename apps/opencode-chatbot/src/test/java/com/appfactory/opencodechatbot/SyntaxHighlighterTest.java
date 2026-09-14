package com.appfactory.opencodechatbot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.appfactory.opencodechatbot.util.SyntaxHighlighter;

import org.junit.Test;

import java.util.List;

public class SyntaxHighlighterTest {

    @Test
    public void highlightsJavaKeywords() {
        String code = "public void hello() { return; }";
        boolean keywordFound = false;
        for (SyntaxHighlighter.Token t : SyntaxHighlighter.highlight("java", code)) {
            if (t.kind == SyntaxHighlighter.KIND_KEYWORD) {
                assertEquals("public", code.substring(t.start, t.end));
                keywordFound = true;
            }
        }
        assertTrue(keywordFound);
    }

    @Test
    public void highlightsStrings() {
        String code = "String s = \"hello world\";";
        boolean found = false;
        for (SyntaxHighlighter.Token t : SyntaxHighlighter.highlight("java", code)) {
            if (t.kind == SyntaxHighlighter.KIND_STRING) {
                assertEquals("\"hello world\"", code.substring(t.start, t.end));
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test
    public void highlightsLineComments() {
        String code = "int a = 1; // comment";
        boolean found = false;
        for (SyntaxHighlighter.Token t : SyntaxHighlighter.highlight("java", code)) {
            if (t.kind == SyntaxHighlighter.KIND_COMMENT) {
                assertEquals("// comment", code.substring(t.start, t.end));
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test
    public void highlightsNumbers() {
        String code = "x = 42";
        boolean found = false;
        for (SyntaxHighlighter.Token t : SyntaxHighlighter.highlight("python", code)) {
            if (t.kind == SyntaxHighlighter.KIND_NUMBER) {
                assertEquals("42", code.substring(t.start, t.end));
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    public void highlightsPythonKeywords() {
        String code = "def foo():\n    return None";
        int kw = 0;
        for (SyntaxHighlighter.Token t : SyntaxHighlighter.highlight("python", code)) {
            if (t.kind == SyntaxHighlighter.KIND_KEYWORD) {
                kw++;
            }
        }
        assertTrue(kw >= 2); // def + return
    }

    @Test
    public void tokenOffsetsAreOrdered() {
        String code = "// c\nint a = \"s\";";
        List<SyntaxHighlighter.Token> tokens = SyntaxHighlighter.highlight("java", code);
        for (int i = 1; i < tokens.size(); i++) {
            assertTrue(tokens.get(i).start >= tokens.get(i - 1).start);
        }
    }

    @Test
    public void handlesEmptyCode() {
        assertEquals(0, SyntaxHighlighter.highlight("java", "").size());
    }
}