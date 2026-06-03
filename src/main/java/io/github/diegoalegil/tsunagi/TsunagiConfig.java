package io.github.diegoalegil.tsunagi;

import io.github.diegoalegil.tsunagi.cache.MemoryCache;

import java.time.Duration;
import java.util.Optional;

/**
 * Immutable configuration for {@link TsunagiClient}, created through a builder.
 *
 * <pre>{@code
 * TsunagiConfig config = TsunagiConfig.builder()
 *         .tmdbToken(System.getenv("TMDB_TOKEN"))
 *         .cacheEnabled(true)
 *         .cacheTtl(Duration.ofMinutes(10))
 *         .build();
 * }</pre>
 *
 * <p>Defaults: the TMDb token is absent, the cache is disabled with a 10-minute
 * TTL, and retries are enabled with 3 attempts and a 500&nbsp;ms initial backoff.
 */
public final class TsunagiConfig {

    private final String tmdbToken;
    private final boolean cacheEnabled;
    private final Duration cacheTtl;
    private final int cacheMaxSize;
    private final boolean retryEnabled;
    private final int retryMaxAttempts;
    private final Duration retryInitialDelay;
    private final Duration requestTimeout;
    private final String userAgent;

    private TsunagiConfig(Builder builder) {
        this.tmdbToken = builder.tmdbToken;
        this.cacheEnabled = builder.cacheEnabled;
        this.cacheTtl = builder.cacheTtl;
        this.cacheMaxSize = builder.cacheMaxSize;
        this.retryEnabled = builder.retryEnabled;
        this.retryMaxAttempts = builder.retryMaxAttempts;
        this.retryInitialDelay = builder.retryInitialDelay;
        this.requestTimeout = builder.requestTimeout;
        this.userAgent = builder.userAgent;
    }

    /** The TMDb Bearer token, if one was configured. */
    public Optional<String> tmdbToken() {
        return Optional.ofNullable(tmdbToken);
    }

    /** Whether search results are cached in memory. */
    public boolean cacheEnabled() {
        return cacheEnabled;
    }

    /** How long a cached result stays valid. */
    public Duration cacheTtl() {
        return cacheTtl;
    }

    /** Maximum number of entries kept in the cache before LRU eviction. */
    public int cacheMaxSize() {
        return cacheMaxSize;
    }

    /** Whether transient failures are retried with exponential backoff. */
    public boolean retryEnabled() {
        return retryEnabled;
    }

    /** Total number of attempts per source call when retries are enabled. */
    public int retryMaxAttempts() {
        return retryMaxAttempts;
    }

    /** Initial backoff delay before the first retry. */
    public Duration retryInitialDelay() {
        return retryInitialDelay;
    }

    /** Maximum time to wait for a single HTTP response from a source. */
    public Duration requestTimeout() {
        return requestTimeout;
    }

    /** The User-Agent header sent to sources, if one was configured. */
    public Optional<String> userAgent() {
        return Optional.ofNullable(userAgent);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String tmdbToken;
        private boolean cacheEnabled = false;
        private Duration cacheTtl = Duration.ofMinutes(10);
        private int cacheMaxSize = MemoryCache.DEFAULT_MAX_SIZE;
        private boolean retryEnabled = true;
        private int retryMaxAttempts = 3;
        private Duration retryInitialDelay = Duration.ofMillis(500);
        private Duration requestTimeout = Duration.ofSeconds(30);
        private String userAgent;

        private Builder() {
        }

        /** Sets the TMDb Bearer token used for enrichment. Optional. */
        public Builder tmdbToken(String tmdbToken) {
            this.tmdbToken = tmdbToken;
            return this;
        }

        /** Enables or disables the in-memory cache. Disabled by default. */
        public Builder cacheEnabled(boolean cacheEnabled) {
            this.cacheEnabled = cacheEnabled;
            return this;
        }

        /** Sets how long cached results stay valid. Defaults to 10 minutes. */
        public Builder cacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
            return this;
        }

        /** Sets the maximum number of cached entries before LRU eviction. Defaults to 1000. */
        public Builder cacheMaxSize(int cacheMaxSize) {
            this.cacheMaxSize = cacheMaxSize;
            return this;
        }

        /** Enables or disables retries on transient failures. Enabled by default. */
        public Builder retryEnabled(boolean retryEnabled) {
            this.retryEnabled = retryEnabled;
            return this;
        }

        /** Sets the total number of attempts per source call. Defaults to 3. */
        public Builder retryMaxAttempts(int retryMaxAttempts) {
            this.retryMaxAttempts = retryMaxAttempts;
            return this;
        }

        /** Sets the initial backoff delay before the first retry. Defaults to 500 ms. */
        public Builder retryInitialDelay(Duration retryInitialDelay) {
            this.retryInitialDelay = retryInitialDelay;
            return this;
        }

        /** Sets the per-request HTTP timeout for every source. Defaults to 30 seconds. */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        /** Sets the User-Agent header sent to sources. Optional; unset by default. */
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public TsunagiConfig build() {
            return new TsunagiConfig(this);
        }
    }
}
