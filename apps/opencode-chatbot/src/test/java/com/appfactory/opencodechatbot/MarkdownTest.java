package com.appfactory.opencodechatbot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.appfactory.opencodechatbot.util.Markdown;

import org.junit.Test;

import java.util.List;

public class MarkdownTest {

    @Test
    public void parsesHeading() {
        Markdown.Document doc = Markdown.parse("# Hello");
        assertEquals(1, doc.getBlocks().size());
        assertEquals(Markdown.BLOCK_HEADING, doc.getBlocks().get(0).kind);
        assertEquals(1, doc.getBlocks().get(0).level);
    }

    @Test
    public void parsesFencedCodeBlockWithLanguage() {
        Markdown.Document doc = Markdown.parse("```java\nint x = 1;\n```");
        assertEquals(1, doc.getBlocks().size());
        Markdown.Block code = doc.getBlocks().get(0);
        assertEquals(Markdown.BLOCK_CODE, code.kind);
        assertEquals("java", code.url);
        assertEquals("int x = 1;\n", code.text);
    }

    @Test
    public void parsesBoldAndItalicInline() {
        Markdown.Document doc = Markdown.parse("This is **bold** and *italic* and `code`.");
        Markdown.Block para = doc.getBlocks().get(0);
        assertEquals(Markdown.BLOCK_PARAGRAPH, para.kind);
        boolean bold = false;
        boolean italic = false;
        boolean code = false;
        for (Markdown.Inline in : para.inlines) {
            if ("bold".equals(in.text)) {
                assertTrue((in.style & Markdown.STYLE_BOLD) != 0);
                bold = true;
            }
            if ("italic".equals(in.text)) {
                assertTrue((in.style & Markdown.STYLE_ITALIC) != 0);
                italic = true;
            }
            if ("code".equals(in.text)) {
                assertTrue((in.style & Markdown.STYLE_CODE_OUT) != 0);
                code = true;
            }
        }
        assertTrue(bold);
        assertTrue(italic);
        assertTrue(code);
    }

    @Test
    public void parsesLink() {
        Markdown.Document doc = Markdown.parse("Visit [GitHub](https://github.com) today.");
        Markdown.Block para = doc.getBlocks().get(0);
        Markdown.Inline link = null;
        for (Markdown.Inline in : para.inlines) {
            if ((in.style & Markdown.STYLE_LINK) != 0) {
                link = in;
            }
        }
        assertTrue(link != null);
        assertEquals("GitHub", link.text);
        assertEquals("https://github.com", link.url);
    }

    @Test
    public void parsesListItems() {
        Markdown.Document doc = Markdown.parse("- one\n- two\n3. three");
        assertEquals(3, doc.getBlocks().size());
        assertEquals(Markdown.BLOCK_LIST_ITEM, doc.getBlocks().get(0).kind);
        assertEquals(Markdown.BLOCK_LIST_ITEM, doc.getBlocks().get(2).kind);
        assertTrue(doc.getBlocks().get(2).ordered);
    }

    @Test
    public void parsesThematicBreak() {
        Markdown.Document doc = Markdown.parse("---");
        assertEquals(Markdown.BLOCK_HR, doc.getBlocks().get(0).kind);
    }

    @Test
    public void parsesBlockquote() {
        Markdown.Document doc = Markdown.parse("> quoted text");
        Markdown.Block quote = doc.getBlocks().get(0);
        assertEquals(Markdown.BLOCK_QUOTE, quote.kind);
        assertEquals("quoted text", Markdown.inlinePlain(quote.inlines));
    }

    @Test
    public void emptyInputYieldsNoBlocks() {
        Markdown.Document doc = Markdown.parse("   \n");
        assertEquals(0, doc.getBlocks().size());
    }

    @Test
    public void multiLineParagraphIsOneBlock() {
        Markdown.Document doc = Markdown.parse("first\nsecond");
        assertEquals(1, doc.getBlocks().size());
        assertEquals(Markdown.BLOCK_PARAGRAPH, doc.getBlocks().get(0).kind);
    }

    @Test
    public void deriveTitleFromFirstLine() {
        String title = com.appfactory.opencodechatbot.model.Conversation.firstContentLine(
                "Hello world, this is a very long first line that should be truncated nicely");
        assertTrue(title.length() <= 41);
    }

    @Test
    public void listMarkerDetection() {
        assertNull(Markdown.listMarker("not a list"));
    }
}