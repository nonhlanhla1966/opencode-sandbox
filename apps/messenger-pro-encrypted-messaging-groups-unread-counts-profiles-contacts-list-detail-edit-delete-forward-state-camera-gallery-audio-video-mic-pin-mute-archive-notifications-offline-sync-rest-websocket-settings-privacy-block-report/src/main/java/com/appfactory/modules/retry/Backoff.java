package com.appfactory.modules.retry;

/** Exponential backoff with jitter. Always used through {@link Policy}. */
public final class Backoff {

    /** Milliseconds of jitter in [0, maxMs]. */
    public static long jitter(long maxMs, java.util.Random rng) {
        if (maxMs <= 0) return 0;
        return (long)(rng.nextDouble() * maxMs);
    }

    /** Exponential delay: min(baseMs * 2^attempt, capMs) + jitter. */
    public static long delay(int attempt, long baseMs, long capMs, long jitterMs) {
        long delay = baseMs << Math.min(attempt, 30);
        delay = Math.min(delay, capMs);
        return delay + jitter(jitterMs, new java.util.Random());
    }

    private Backoff() { }
}