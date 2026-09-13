package com.appfactory.notes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NoteTextTest {

    @Test
    public void titleOf_trimsAndDefaultsToUntitled() {
        assertEquals("Shopping", NoteText.titleOf("  Shopping  "));
        assertEquals(NoteText.UNTITLED, NoteText.titleOf(""));
        assertEquals(NoteText.UNTITLED, NoteText.titleOf("   "));
        assertEquals(NoteText.UNTITLED, NoteText.titleOf(null));
    }

    @Test
    public void bodyOf_trimsEdges() {
        assertEquals("Buy milk", NoteText.bodyOf("  Buy milk \n"));
        assertEquals("", NoteText.bodyOf(null));
    }

    @Test
    public void isBlank_detectsEmptyNote() {
        assertTrue(NoteText.isBlank("", ""));
        assertTrue(NoteText.isBlank("   ", "\n  "));
        assertFalse(NoteText.isBlank("Task", ""));
        assertFalse(NoteText.isBlank("", "Some body"));
    }

    @Test
    public void matches_isCaseInsensitiveAndCoversBody() {
        assertTrue(NoteText.matches("Grocery", "Buy eggs and milk", "eggs"));
        assertTrue(NoteText.matches("Grocery", "Buy eggs", "GROCERY"));
        assertTrue(NoteText.matches("Grocery", "Buy milk", "MiLk"));
        assertFalse(NoteText.matches("Grocery", "Buy eggs", "bread"));
        assertTrue(NoteText.matches("Any", "Anything", ""));
        assertTrue(NoteText.matches("Any", "Anything", null));
        assertTrue(NoteText.matches("Any", "Anything", "   "));
    }

    @Test
    public void snippet_usesFirstLineAndTruncates() {
        assertEquals("Just one line", NoteText.snippet("Just one line", 80));
        assertEquals("Second", NoteText.snippet("Second\nThird line", 80));
        assertEquals("", NoteText.snippet("", 80));
        assertEquals("", NoteText.snippet(null, 80));
        String longLine = "abcdefghijklmnopqrstuvwxyz";
        String s = NoteText.snippet(longLine, 10);
        assertTrue(s.length() <= 10);
        assertTrue(s.endsWith("\u2026"));
    }
}