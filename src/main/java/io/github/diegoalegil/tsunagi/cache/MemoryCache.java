package io.github.diegoalegil.tsunagi.cache;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * A thread-safe in-memory cache where every entry expires after a fixed
 * time-to-live (TTL) and the total number of entries is bounded.
 *
 * <p>Repeated searches for the same title can be served from here instead of
 * hitting the APIs again, which is faster and kinder to the rate limits.
 * Expired entries are dropped lazily the next time their key is read, and when
 * the cache is full the least-recently-used entry is evicted, so memory usage
 * stays bounded even under high-cardinality workloads.
 *
 * <p>Time is read through an injectable {@link LongSupplier} of nanoseconds, so
 * expiry can be tested deterministically without real waiting.
 *
 * @param <K> the key type (typically the search title)
 * @param <V> the value type (typically the search result)
 */
public final class MemoryCache<K, V> {

    /** Default maximum number of entries when no size is given. */
    public static final int DEFAULT_MAX_SIZE = 1000;

    private final Map<K, CacheEntry<V>> store;
    private final long ttlNanos;
    private final LongSupplier nanoClock;

    /** Creates a cache with the default maximum size. */
    public MemoryCache(Duration ttl) {
        this(ttl, DEFAULT_MAX_SIZE, System::nanoTime);
    }

    /** Creates a cache that holds at most {@code maxSize} entries. */
    public MemoryCache(Duration ttl, int maxSize) {
        this(ttl, maxSize, System::nanoTime);
    }

    MemoryCache(Duration ttl, LongSupplier nanoClock) {
        this(ttl, DEFAULT_MAX_SIZE, nanoClock);
    }

    MemoryCache(Duration ttl, int maxSize, LongSupplier nanoClock) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive, got " + ttl);
        }
        if (maxSize < 1) {
            throw new IllegalArgumentException("maxSize must be at least 1, got " + maxSize);
        }
        this.ttlNanos = ttl.toNanos();
        this.nanoClock = nanoClock;
        // Access-order LinkedHashMap evicts the least-recently-used entry once the
        // size exceeds maxSize.
        this.store = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CacheEntry<V>> eldest) {
                return size() > maxSize;
            }
        });
    }

    /**
     * Returns the cached value for {@code key} if present and still valid.
     * An expired entry is removed and reported as absent.
     */
    public Optional<V> get(K key) {
        synchronized (store) {
            CacheEntry<V> entry = store.get(key);
            if (entry == null) {
                return Optional.empty();
            }
            if (entry.isExpired(nanoClock.getAsLong())) {
                store.remove(key);
                return Optional.empty();
            }
            return Optional.of(entry.value());
        }
    }

    /** Stores {@code value} under {@code key}, resetting its TTL from now. */
    public void put(K key, V value) {
        long expiresAtNanos = nanoClock.getAsLong() + ttlNanos;
        synchronized (store) {
            store.put(key, new CacheEntry<>(value, expiresAtNanos));
        }
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
