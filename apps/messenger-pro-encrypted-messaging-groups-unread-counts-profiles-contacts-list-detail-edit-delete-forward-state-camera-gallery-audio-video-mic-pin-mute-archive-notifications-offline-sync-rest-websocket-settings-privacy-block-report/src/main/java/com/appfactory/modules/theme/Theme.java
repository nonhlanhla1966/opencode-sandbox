package com.appfactory.modules.theme;

/** Deterministic theme helpers: palette derivation and contrast (pure JVM). */
public final class Theme {

    public enum Mode { SYSTEM, LIGHT, DARK }

    /** Deterministic 24-bit RGB color from a string seed (MD5 not required). */
    public static int colorFromSeed(String seed, int base) {
        int h = seed.hashCode();
        int r = (base >> 16 & 0xFF) ^ ((h >> 8) & 0x1F);
        int g = (base >> 8 & 0xFF) ^ ((h >> 4) & 0x1F);
        int b = (base & 0xFF) ^ (h & 0x1F);
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    public static double luminance(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        double rs = r / 255.0, gs = g / 255.0, bs = b / 255.0;
        rs = rs <= 0.03928 ? rs / 12.92 : Math.pow((rs + 0.055) / 1.055, 2.4);
        gs = gs <= 0.03928 ? gs / 12.92 : Math.pow((gs + 0.055) / 1.055, 2.4);
        bs = bs <= 0.03928 ? bs / 12.92 : Math.pow((bs + 0.055) / 1.055, 2.4);
        return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs;
    }

    /** WCAG contrast ratio between two colors (1.0 .. 21.0). */
    public static double contrastRatio(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        double lighter = Math.max(la, lb), darker = Math.min(la, lb);
        return (lighter + 0.05) / (darker + 0.05);
    }

    public static boolean isReadable(int fg, int bg) {
        return contrastRatio(fg, bg) >= 4.5;
    }
}