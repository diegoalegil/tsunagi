package io.github.diegoalegil.tsunagi.cache;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * A simple thread-safe in-memory cache where every entry expires after a fixed
 * time-to-live (TTL).
 *
 * <p>Repeated searches for the same title can be served from here instead of
 * hitting the APIs again, which is faster and kinder to the rate limits.
 * Expired entries are dropped lazily the next time their key is read.
 *
 * <p>Time is read through an injectable {@link LongSupplier} of nanoseconds, so
 * expiry can be tested deterministically without real waiting.
 *
 * @param <K> the key type (typically the search title)
 * @param <V> the value type (typically the search result)
 */
public final class MemoryCache<K, V> {

    private final Map<K, CacheEntry<V>> store = new ConcurrentHashMap<>();
    private final long ttlNanos;
    private final LongSupplier nanoClock;

    /**
     * Creates a cache whose entries live for {@code ttl}.
     *
     * @param ttl how long each entry stays valid; must be positive
     */
    public MemoryCache(Duration ttl) {
        this(ttl, System::nanoTime);
    }

    MemoryCache(Duration ttl, LongSupplier nanoClock) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive, got " + ttl);
        }
        this.ttlNanos = ttl.toNanos();
        this.nanoClock = nanoClock;
    }

    /**
     * Returns the cached value for {@code key} if present and still valid.
     * An expired entry is removed and reported as absent.
     */
    public Optional<V> get(K key) {
        CacheEntry<V> entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired(nanoClock.getAsLong())) {
            store.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    /** Stores {@code value} under {@code key}, resetting its TTL from now. */
    public void put(K key, V value) {
        long expiresAtNanos = nanoClock.getAsLong() + ttlNanos;
        store.put(key, new CacheEntry<>(value, expiresAtNanos));
    }

    /** Removes every entry. */
    public void clear() {
        store.clear();
    }

    /** The number of entries currently held, including any not-yet-evicted expired ones. */
    public int size() {
        return store.size();
    }
}
