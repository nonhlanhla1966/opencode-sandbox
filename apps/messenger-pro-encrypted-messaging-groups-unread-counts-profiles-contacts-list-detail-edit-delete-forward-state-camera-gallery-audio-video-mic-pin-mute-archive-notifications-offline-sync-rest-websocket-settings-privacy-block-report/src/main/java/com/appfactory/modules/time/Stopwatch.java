package com.appfactory.modules.time;

/** Simple wall-clock measurement with a settable clock (for tests). */
public final class Stopwatch {

    public interface Clock {
        long now();
    }

    private static Clock clock = System::currentTimeMillis;

    /** For tests: override the clock used by all stopwatches. */
    public static void setClock(Clock c) { clock = c; }
    public static void resetClock() { clock = System::currentTimeMillis; }

    private long start;

    public Stopwatch() { start = clock.now(); }

    public void reset() { start = clock.now(); }

    public long elapsedMs() { return Math.max(0, clock.now() - start); }

    public long elapsedSeconds() { return elapsedMs() / 1000L; }

    public static String human(Duration d) {
        long ms = d.ms;
        long totalSec = ms / 1000;
        long h = totalSec / 3600, m = (totalSec % 3600) / 60, s = totalSec % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (m > 0 || h > 0) {
            sb.append(h > 0 ? String.format("%02d", m) : String.valueOf(m)).append("m ");
        }
        String suffix = (h > 0 || m > 0) ? String.format("%02d", s) : String.valueOf(s);
        sb.append(suffix).append("s");
        return sb.toString();
    }

    public static final class Duration {
        public final long ms;
        public Duration(long ms) { this.ms = ms; }
        public static Duration of(long ms) { return new Duration(ms); }
        @Override public String toString() { return human(this); }
    }
}