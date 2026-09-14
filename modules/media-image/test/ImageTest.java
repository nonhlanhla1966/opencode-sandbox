package com.appfactory.modules.media;

import org.junit.Test;
import static org.junit.Assert.*;

public class ImageTest {
    @Test public void aspectRatios() {
        assertEquals("4:3", Image.aspectRatio(4032, 3024));
        assertEquals("16:9", Image.aspectRatio(1920, 1080));
        assertEquals("1:1", Image.aspectRatio(800, 800));
    }
    @Test public void sampleSizesScaleByPowersOfTwo() {
        assertEquals(1, Image.sampleSize(4000, 3000, 4000));      // sqrt area fits
        assertEquals(2, Image.sampleSize(8000, 8000, 4000));
        assertEquals(4, Image.sampleSize(10000, 10000, 2500));
    }
    @Test public void validDimensionCheck() {
        assertTrue(Image.isValidDimension(1920, 1080));
        assertFalse(Image.isValidDimension(0, 100));
        assertTrue(Image.gcd(0, 5) == 5);
    }
}