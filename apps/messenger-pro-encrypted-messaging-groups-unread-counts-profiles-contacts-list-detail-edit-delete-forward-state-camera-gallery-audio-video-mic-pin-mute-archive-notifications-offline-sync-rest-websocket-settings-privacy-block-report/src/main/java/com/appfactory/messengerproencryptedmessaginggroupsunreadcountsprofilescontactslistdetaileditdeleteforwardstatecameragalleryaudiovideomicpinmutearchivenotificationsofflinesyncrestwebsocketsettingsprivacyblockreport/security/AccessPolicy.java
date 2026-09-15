package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.security;

/**
 * Pure lockout/attempt gate (e.g. before decrypting a vault). Deterministic
 * with an injected clock; unit-testable.
 */
public final class AccessPolicy {

    public interface Clock {
        long now();
    }

    public static final class AccessResult {
        public final boolean allowed;
        public final long retryAt;   // 0 when allowed
        public final int remaining;  // remaining tries before lockout

        AccessResult(boolean allowed, long retryAt, int remaining) {
            this.allowed = allowed;
            this.retryAt = retryAt;
            this.remaining = remaining;
        }
    }

    private final int maxAttempts;
    private final long lockoutMs;
    private final Clock clock;

    private int failures;
    private long lockedUntil;

    public AccessPolicy(int maxAttempts, long lockoutMs, Clock clock) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.lockoutMs = Math.max(0, lockoutMs);
        this.clock = clock;
    }

    public synchronized AccessResult attempt() {
        long now = clock.now();
        if (now < lockedUntil) {
            return new AccessResult(false, lockedUntil, maxAttempts - failures);
        }
        lockedUntil = 0;
        return new AccessResult(true, 0, maxAttempts - failures);
    }

    public synchronized void recordFailure() {
        failures++;
        if (failures >= maxAttempts) {
            lockedUntil = clock.now() + lockoutMs;
            failures = 0;
        }
    }

    public synchronized void recordSuccess() {
        failures = 0;
        lockedUntil = 0;
    }

    public synchronized boolean lockedNow() {
        return clock.now() < lockedUntil;
    }
}