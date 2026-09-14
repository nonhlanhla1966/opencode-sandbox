package com.appfactory.modules.retry;

import org.junit.Test;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class RetryTest {
    @Test public void retriesUntilSuccess() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        String out = Retry.run(() -> {
            if (attempts.incrementAndGet() < 3) throw new IOException("flaky");
            return "ok";
        }, 5, 1);
        assertEquals("ok", out);
        assertEquals(3, attempts.get());
    }
    @Test public void givesUpAfterMax() {
        AtomicInteger attempts = new AtomicInteger();
        try {
            Retry.run(() -> { attempts.incrementAndGet(); throw new IOException("nope"); },
                    3, 1);
            fail("should throw");
        } catch (Exception e) {
            assertEquals(3, attempts.get());
        }
    }
    @Test public void respectspredicate() {
        AtomicInteger a = new AtomicInteger();
        try {
            Retry.run(() -> { a.incrementAndGet(); throw new IllegalStateException("no"); },
                    5, 1, Retry.RETRY_IO);
            fail("should throw");
        } catch (Exception e) {
            assertEquals(1, a.get());   // not retried (non-IO)
        }
    }
    @Test public void backoffStaysBounded() {
        assertEquals(64, Backoff.delay(3, 8, 1000, 0));
        assertEquals(1000, Backoff.delay(10, 8, 1000, 0));
    }
}