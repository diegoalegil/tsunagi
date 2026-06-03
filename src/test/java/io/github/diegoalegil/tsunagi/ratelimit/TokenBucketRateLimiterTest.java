package io.github.diegoalegil.tsunagi.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketRateLimiterTest {

    /** A clock whose time only advances when the test tells it to. */
    private static final class FakeClock implements LongSupplier {
        private long nanos;

        @Override
        public long getAsLong() {
            return nanos;
        }

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }
    }

    @Test
    void startsFullAndAllowsAnInitialBurst() {
        FakeClock clock = new FakeClock();
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, Duration.ofSeconds(1), clock);

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire(), "bucket should be empty after the burst");
    }

    @Test
    void refillsOverTime() {
        FakeClock clock = new FakeClock();
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, Duration.ofSeconds(1), clock);

        // Drain the bucket.
        limiter.tryAcquire();
        limiter.tryAcquire();
        limiter.tryAcquire();
        assertFalse(limiter.tryAcquire());

        // After a third of a second, exactly one token (3 per second) should be back.
        clock.advance(Duration.ofMillis(334));
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire(), "only one token should have been refilled");
    }

    @Test
    void neverExceedsCapacityNoMatterHowLongItWaits() {
        FakeClock clock = new FakeClock();
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, Duration.ofSeconds(1), clock);

        // Idle for an hour: tokens must still be capped at the capacity of 3.
        clock.advance(Duration.ofHours(1));

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire(), "tokens must not accumulate beyond capacity");
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenBucketRateLimiter(0, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new TokenBucketRateLimiter(-1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new TokenBucketRateLimiter(3, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new TokenBucketRateLimiter(3, Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> new TokenBucketRateLimiter(3, null));
    }

    @Test
    void acquireBlocksUntilATokenIsRefilled() throws InterruptedException {
        // Real clock here: 1 token per 200 ms.
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, Duration.ofMillis(200));

        limiter.acquire(); // consumes the only starting token immediately

        long start = System.nanoTime();
        limiter.acquire(); // must wait for the bucket to refill
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        // Generous lower bound to stay reliable on slow CI machines.
        assertTrue(elapsedMillis >= 120,
                "second acquire should have blocked ~200ms but waited " + elapsedMillis + "ms");
    }
}
