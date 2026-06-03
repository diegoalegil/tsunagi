package io.github.diegoalegil.tsunagi.http;

import io.github.diegoalegil.tsunagi.exception.ApiException;
import io.github.diegoalegil.tsunagi.exception.RateLimitException;
import io.github.diegoalegil.tsunagi.exception.SourceUnavailableException;
import io.github.diegoalegil.tsunagi.exception.TsunagiException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryPolicyTest {

    /** A sleeper that records the requested delays instead of really waiting. */
    private static final class RecordingSleeper implements RetryPolicy.Sleeper {
        final List<Duration> delays = new ArrayList<>();

        @Override
        public void sleep(Duration duration) {
            delays.add(duration);
        }
    }

    private RetryPolicy policy(int maxAttempts, Duration initialDelay, RetryPolicy.Sleeper sleeper) {
        return new RetryPolicy(maxAttempts, initialDelay, 2.0, sleeper);
    }

    private static SourceUnavailableException transientError() {
        return new SourceUnavailableException("AniList", "request failed", new IOException("down"));
    }

    @Test
    void returnsImmediatelyWhenOperationSucceeds() {
        RecordingSleeper sleeper = new RecordingSleeper();
        RetryPolicy retry = policy(3, Duration.ofMillis(10), sleeper);

        String result = retry.execute(() -> "ok");

        assertEquals("ok", result);
        assertTrue(sleeper.delays.isEmpty(), "no sleep should happen on success");
    }

    @Test
    void retriesTransientFailureThenSucceeds() {
        RecordingSleeper sleeper = new RecordingSleeper();
        RetryPolicy retry = policy(3, Duration.ofMillis(10), sleeper);
        AtomicInteger calls = new AtomicInteger();

        String result = retry.execute(() -> {
            if (calls.getAndIncrement() < 1) {
                throw transientError();
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(2, calls.get());
        assertEquals(1, sleeper.delays.size());
    }

    @Test
    void throwsTheLastErrorAfterExhaustingAttempts() {
        RecordingSleeper sleeper = new RecordingSleeper();
        RetryPolicy retry = policy(3, Duration.ofMillis(10), sleeper);
        AtomicInteger calls = new AtomicInteger();

        Supplier<String> alwaysFailing = () -> {
            calls.incrementAndGet();
            throw transientError();
        };

        assertThrows(SourceUnavailableException.class, () -> retry.execute(alwaysFailing));
        assertEquals(3, calls.get(), "should try exactly maxAttempts times");
        assertEquals(2, sleeper.delays.size(), "should sleep between attempts only");
    }

    @Test
    void doesNotRetryClientErrors() {
        RecordingSleeper sleeper = new RecordingSleeper();
        RetryPolicy retry = policy(3, Duration.ofMillis(10), sleeper);
        AtomicInteger calls = new AtomicInteger();

        Supplier<String> notFound = () -> {
            calls.incrementAndGet();
            throw new ApiException("TMDb", 404);
        };

        assertThrows(ApiException.class, () -> retry.execute(notFound));
        assertEquals(1, calls.get(), "4xx errors must not be retried");
        assertTrue(sleeper.delays.isEmpty());
    }

    @Test
    void retriesServerErrorsAndRateLimits() {
        RetryPolicy retry = policy(2, Duration.ofMillis(10), new RecordingSleeper());

        AtomicInteger serverCalls = new AtomicInteger();
        assertThrows(ApiException.class, () -> retry.execute(() -> {
            serverCalls.incrementAndGet();
            throw new ApiException("Jikan", 503);
        }));
        assertEquals(2, serverCalls.get());

        AtomicInteger rateCalls = new AtomicInteger();
        assertThrows(RateLimitException.class, () -> retry.execute(() -> {
            rateCalls.incrementAndGet();
            throw new RateLimitException("Jikan");
        }));
        assertEquals(2, rateCalls.get());
    }

    @Test
    void backoffDelaysGrowExponentially() {
        RecordingSleeper sleeper = new RecordingSleeper();
        RetryPolicy retry = policy(4, Duration.ofMillis(100), sleeper);

        assertThrows(TsunagiException.class, () -> retry.execute(() -> {
            throw transientError();
        }));

        // 4 attempts -> 3 waits, doubling each time: 100ms, 200ms, 400ms.
        assertEquals(3, sleeper.delays.size());
        assertEquals(Duration.ofMillis(100), sleeper.delays.get(0));
        assertEquals(Duration.ofMillis(200), sleeper.delays.get(1));
        assertEquals(Duration.ofMillis(400), sleeper.delays.get(2));
    }

    @Test
    void backoffIsCappedAtSixtySeconds() {
        RecordingSleeper sleeper = new RecordingSleeper();
        RetryPolicy retry = policy(5, Duration.ofSeconds(40), sleeper);

        assertThrows(TsunagiException.class, () -> retry.execute(() -> {
            throw transientError();
        }));

        // 40s, then min(60s, 80s)=60s, capped from there on.
        assertEquals(4, sleeper.delays.size());
        assertEquals(Duration.ofSeconds(40), sleeper.delays.get(0));
        assertEquals(Duration.ofSeconds(60), sleeper.delays.get(1));
        assertEquals(Duration.ofSeconds(60), sleeper.delays.get(2));
        assertEquals(Duration.ofSeconds(60), sleeper.delays.get(3));
    }

    @Test
    void rejectsInvalidArguments() {
        RecordingSleeper sleeper = new RecordingSleeper();
        assertThrows(IllegalArgumentException.class, () -> policy(0, Duration.ofMillis(10), sleeper));
        assertThrows(IllegalArgumentException.class, () -> policy(3, Duration.ZERO, sleeper));
        assertThrows(IllegalArgumentException.class, () -> policy(3, Duration.ofMillis(-5), sleeper));
        assertThrows(IllegalArgumentException.class,
                () -> new RetryPolicy(3, Duration.ofMillis(10), 0.5, sleeper));
    }
}
