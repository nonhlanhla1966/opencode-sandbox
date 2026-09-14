package com.appfactory.modules.time;

import org.junit.Test;
import static org.junit.Assert.*;

public class StopwatchTest {

    private static final class FakeClock implements Stopwatch.Clock {
        long now = 0;
        public long now() { return now; }
    }

    @Test public void measuresElapsed() {
        FakeClock c = new FakeClock();
        Stopwatch.setClock(c);
        try {
            Stopwatch sw = new Stopwatch();
            c.now = 5_000;
            assertEquals(5_000, sw.elapsedMs());
            c.now = 8_500;
            assertEquals(8_500, sw.elapsedMs());
            assertEquals(8L, sw.elapsedSeconds());
            sw.reset();
            assertEquals(0, sw.elapsedMs());
        } finally {
            Stopwatch.resetClock();
        }
    }
    @Test public void formatting() {
        assertEquals("5s", Stopwatch.human(Stopwatch.Duration.of(5_000)));
        assertEquals("1m 05s", Stopwatch.human(Stopwatch.Duration.of(65_000)));
    }
}