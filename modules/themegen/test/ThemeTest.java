package com.appfactory.modules.theme;

import org.junit.Test;
import static org.junit.Assert.*;

public class ThemeTest {
    @Test public void deterministicPalette() {
        int c1 = Theme.colorFromSeed("notes-app", 0xFF6750A4);
        int c2 = Theme.colorFromSeed("notes-app", 0xFF6750A4);
        assertEquals(c1, c2);
        assertTrue(Theme.colorFromSeed("a", 0xFF000000) != Theme.colorFromSeed("b", 0xFF000000));
    }
    @Test public void contrastRatioKnown() {
        // black on white -> 21
        assertEquals(21.0, Theme.contrastRatio(0xFF000000, 0xFFFFFFFF), 0.01);
        // white on white -> 1
        assertEquals(1.0, Theme.contrastRatio(0xFFFFFFFF, 0xFFFFFFFF), 0.01);
    }
    @Test public void readability() {
        assertTrue(Theme.isReadable(0xFF000000, 0xFFFFFFFF));
        assertFalse(Theme.isReadable(0xFFFFFFFF, 0xFFFFFFFF));
    }
}