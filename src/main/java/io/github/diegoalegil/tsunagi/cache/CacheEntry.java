package io.github.diegoalegil.tsunagi.cache;

/**
 * A cached value together with the time (in {@code System.nanoTime()} units) at
 * which it expires. Internal to {@link MemoryCache}.
 */
record CacheEntry<V>(V value, long expiresAtNanos) {

    boolean isExpired(long nowNanos) {
        return nowNanos >= expiresAtNanos;
    }
}
