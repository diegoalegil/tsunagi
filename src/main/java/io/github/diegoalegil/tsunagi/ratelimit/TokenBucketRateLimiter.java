package io.github.diegoalegil.tsunagi.ratelimit;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * A thread-safe <em>token bucket</em> rate limiter.
 *
 * <p>The bucket holds up to {@code permits} tokens and refills continuously at a
 * rate of {@code permits} tokens per {@code period}. Every call to
 * {@link #acquire()} or a successful {@link #tryAcquire()} consumes one token.
 * When the bucket is empty, {@link #acquire()} blocks until a token has been
 * refilled.
 *
 * <p>The bucket starts full, so an initial burst of up to {@code permits} calls
 * is allowed before the rate kicks in. This is what public APIs such as Jikan
 * (3 requests per second) expect.
 *
 * <pre>{@code
 * TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, Duration.ofSeconds(1));
 * limiter.acquire(); // returns immediately while tokens remain, blocks otherwise
 * }</pre>
 *
 * <p>The current time is read through an injectable {@link LongSupplier} of
 * nanoseconds. Production code uses {@link System#nanoTime()}; tests inject a
 * fake clock to advance time deterministically without real waiting.
 */
public final class TokenBucketRateLimiter {

    private final double capacity;
    private final double refillTokensPerNano;
    private final LongSupplier nanoClock;

    private final Object lock = new Object();
    private double availableTokens;
    private long lastRefillNanos;

    /**
     * Creates a limiter that allows {@code permits} calls per {@code period}.
     *
     * @param permits maximum number of tokens (also the burst size); must be positive
     * @param period  the time window over which {@code permits} tokens are refilled;
     *                must be a positive duration
     */
    public TokenBucketRateLimiter(long permits, Duration period) {
        this(permits, period, System::nanoTime);
    }

    /**
     * Package-private constructor that accepts a custom time source, used by tests
     * to control the passage of time.
     */
    TokenBucketRateLimiter(long permits, Duration period, LongSupplier nanoClock) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be positive, got " + permits);
        }
        if (period == null || period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("period must be a positive duration, got " + period);
        }

        this.capacity = permits;
        this.refillTokensPerNano = (double) permits / period.toNanos();
        this.nanoClock = nanoClock;
        this.availableTokens = permits;
        this.lastRefillNanos = nanoClock.getAsLong();
    }

    /**
     * Consumes one token if one is available right now, without blocking.
     *
     * @return {@code true} if a token was consumed, {@code false} if the bucket was empty
     */
    public boolean tryAcquire() {
        synchronized (lock) {
            refill();
            if (availableTokens >= 1.0) {
                availableTokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    /**
     * Consumes one token, blocking the calling thread until one becomes available.
     *
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public void acquire() throws InterruptedException {
        while (true) {
            long waitNanos;
            synchronized (lock) {
                refill();
                if (availableTokens >= 1.0) {
                    availableTokens -= 1.0;
                    return;
                }
                double missingTokens = 1.0 - availableTokens;
                waitNanos = (long) Math.ceil(missingTokens / refillTokensPerNano);
            }
            // Sleep outside the lock so other threads can make progress meanwhile.
            if (waitNanos > 0) {
                TimeUnit.NANOSECONDS.sleep(waitNanos);
            }
        }
    }

    /**
     * Adds the tokens earned since the last refill, capped at the bucket capacity.
     * Must be called while holding {@link #lock}.
     */
    private void refill() {
        long now = nanoClock.getAsLong();
        long elapsedNanos = now - lastRefillNanos;
        if (elapsedNanos > 0) {
            availableTokens = Math.min(capacity, availableTokens + elapsedNanos * refillTokensPerNano);
            lastRefillNanos = now;
        }
    }
}
