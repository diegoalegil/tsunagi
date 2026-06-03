package io.github.diegoalegil.tsunagi.http;

import io.github.diegoalegil.tsunagi.exception.ApiException;
import io.github.diegoalegil.tsunagi.exception.RateLimitException;
import io.github.diegoalegil.tsunagi.exception.SourceUnavailableException;
import io.github.diegoalegil.tsunagi.exception.TsunagiException;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Retries an operation that fails with a <em>transient</em> error, waiting an
 * exponentially growing amount of time between attempts.
 *
 * <p>Only transient failures are retried: a source being unreachable
 * ({@link SourceUnavailableException}), a rate-limit rejection
 * ({@link RateLimitException}) or a server error ({@link ApiException} with a
 * 5xx status). Client errors (4xx other than 429) and parsing errors are not
 * retried, because retrying them would not help.
 *
 * <pre>{@code
 * RetryPolicy retry = RetryPolicy.exponentialBackoff(3, Duration.ofMillis(500));
 * Optional<Anime> anime = retry.execute(() -> client.searchAnime("Frieren"));
 * }</pre>
 *
 * <p>The wait between attempts goes through an injectable {@link Sleeper} so the
 * backoff schedule can be tested without real waiting.
 */
public final class RetryPolicy {

    /** Pauses the current thread for the given duration. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    /** Upper bound on a single backoff wait, so exponential growth can never overflow. */
    private static final long MAX_BACKOFF_NANOS = Duration.ofSeconds(60).toNanos();

    private final int maxAttempts;
    private final Duration initialDelay;
    private final double multiplier;
    private final Sleeper sleeper;

    /**
     * Creates a policy with exponential backoff and a doubling delay.
     *
     * @param maxAttempts  total number of attempts (must be at least 1)
     * @param initialDelay delay before the first retry (must be positive)
     */
    public static RetryPolicy exponentialBackoff(int maxAttempts, Duration initialDelay) {
        return new RetryPolicy(maxAttempts, initialDelay, 2.0, defaultSleeper());
    }

    RetryPolicy(int maxAttempts, Duration initialDelay, double multiplier, Sleeper sleeper) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1, got " + maxAttempts);
        }
        if (initialDelay == null || initialDelay.isNegative() || initialDelay.isZero()) {
            throw new IllegalArgumentException("initialDelay must be positive, got " + initialDelay);
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be at least 1.0, got " + multiplier);
        }
        this.maxAttempts = maxAttempts;
        this.initialDelay = initialDelay;
        this.multiplier = multiplier;
        this.sleeper = sleeper;
    }

    /**
     * Runs {@code operation}, retrying transient failures up to {@code maxAttempts}
     * times with exponential backoff.
     *
     * @return whatever the operation returns once it succeeds
     * @throws TsunagiException the last failure if every attempt fails, or a
     *                          non-retryable failure as soon as it happens
     */
    public <T> T execute(Supplier<T> operation) {
        Duration delay = initialDelay;
        int attempt = 1;

        while (true) {
            try {
                return operation.get();
            } catch (TsunagiException e) {
                if (attempt >= maxAttempts || !isRetryable(e)) {
                    throw e;
                }
                sleep(delay);
                long nextNanos = (long) Math.min(MAX_BACKOFF_NANOS, delay.toNanos() * multiplier);
                delay = Duration.ofNanos(nextNanos);
                attempt++;
            }
        }
    }

    private boolean isRetryable(TsunagiException e) {
        if (e instanceof SourceUnavailableException || e instanceof RateLimitException) {
            return true;
        }
        if (e instanceof ApiException api) {
            return api.statusCode() >= 500;
        }
        return false;
    }

    private void sleep(Duration delay) {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TsunagiException("Interrupted while waiting to retry", e);
        }
    }

    private static Sleeper defaultSleeper() {
        return duration -> Thread.sleep(duration.toMillis(), duration.toNanosPart() % 1_000_000);
    }
}
