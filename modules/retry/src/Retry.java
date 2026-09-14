package com.appfactory.modules.retry;

/**
 * Bounded retry runner. Retries a block when it throws, up to maxAttempts,
 * sleeping an exponentially growing delay between attempts.
 */
public final class Retry {

    public interface Action<T> {
        T run() throws Exception;
    }
    public interface RetryablePredicate {
        boolean shouldRetry(Exception e);
    }

    public static final RetryablePredicate RETRY_ALL = e -> true;
    public static final RetryablePredicate RETRY_IO =
            e -> e instanceof java.io.IOException || e instanceof java.net.SocketException;

    public static <T> T run(Action<T> action, int maxAttempts,
                            long baseMs, RetryablePredicate predicate)
            throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.run();
            } catch (Exception e) {
                last = e;
                if (attempt == maxAttempts || !predicate.shouldRetry(e)) break;
                long delay = Backoff.delay(attempt, baseMs, 2_000, 0);
                Thread.sleep(delay);
            }
        }
        throw last;
    }

    public static <T> T run(Action<T> action, int maxAttempts, long baseMs)
            throws Exception {
        return run(action, maxAttempts, baseMs, RETRY_ALL);
    }

    private Retry() { }
}