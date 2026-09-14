package com.appfactory.modules.media;

/** Pure image math: aspect ratios and safe sample sizes (no deps). */
public final class Image {
    private Image() { }

    public static final class Dim {
        public final int width, height;
        public Dim(int width, int height) {
            this.width = width; this.height = height;
        }
    }

    /** GCD-based aspect ratio, e.g. (4032, 3024) -> 4:3. */
    public static String aspectRatio(int width, int height) {
        int g = gcd(width, height);
        if (g == 0) return "0:0";
        return (width / g) + ":" + (height / g);
    }

    public static int gcd(int a, int b) {
        a = Math.abs(a); b = Math.abs(b);
        while (b != 0) { int t = b; b = a % b; a = t; }
        return a;
    }

    /** Power-of-two sample size that keeps the decoded image under maxPx. */
    public static int sampleSize(int width, int height, int maxPx) {
        int sample = 1;
        while (width / sample > maxPx || height / sample > maxPx) {
            sample *= 2;
        }
        return sample;
    }

    public static boolean isValidDimension(int width, int height) {
        return width > 0 && height > 0 && width <= 32640 && height <= 32640;
    }
}