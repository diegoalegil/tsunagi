package io.github.diegoalegil.tsunagi.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryCacheTest {

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
    void returnsEmptyForMissingKey() {
        MemoryCache<String, String> cache = new MemoryCache<>(Duration.ofMinutes(1), new FakeClock());

        assertTrue(cache.get("missing").isEmpty());
    }

    @Test
    void returnsStoredValueWithinTtl() {
        FakeClock clock = new FakeClock();
        MemoryCache<String, String> cache = new MemoryCache<>(Duration.ofMinutes(10), clock);

        cache.put("frieren", "Frieren");
        clock.advance(Duration.ofMinutes(9));

        assertEquals(Optional.of("Frieren"), cache.get("frieren"));
    }

    @Test
    void expiresValueAfterTtl() {
        FakeClock clock = new FakeClock();
        MemoryCache<String, String> cache = new MemoryCache<>(Duration.ofMinutes(10), clock);

        cache.put("frieren", "Frieren");
        clock.advance(Duration.ofMinutes(10));

        assertTrue(cache.get("frieren").isEmpty(), "entry should be expired at exactly the TTL");
    }

    @Test
    void removesExpiredEntryOnAccess() {
        FakeClock clock = new FakeClock();
        MemoryCache<String, String> cache = new MemoryCache<>(Duration.ofMinutes(1), clock);

        cache.put("k", "v");
        assertEquals(1, cache.size());

        clock.advance(Duration.ofMinutes(2));
        cache.get("k"); // triggers lazy eviction

        assertEquals(0, cache.size());
    }

    @Test
    void overwritingAKeyResetsItsTtl() {
        FakeClock clock = new FakeClock();
        MemoryCache<String, String> cache = new MemoryCache<>(Duration.ofMinutes(10), clock);

        cache.put("k", "first");
        clock.advance(Duration.ofMinutes(8));
        cache.put("k", "second"); // fresh 10-minute TTL from here

        clock.advance(Duration.ofMinutes(8)); // 16 min after the first put, 8 after the second
        assertEquals(Optional.of("second"), cache.get("k"));
    }

    @Test
    void clearRemovesEverything() {
        MemoryCache<String, String> cache = new MemoryCache<>(Duration.ofMinutes(1), new FakeClock());

        cache.put("a", "1");
        cache.put("b", "2");
        cache.clear();

        assertEquals(0, cache.size());
        assertTrue(cache.get("a").isEmpty());
    }

    @Test
    void evictsLeastRecentlyUsedEntryWhenFull() {
        MemoryCache<String, String> cache = new MemoryCache<>(Duration.ofMinutes(10), 2);

        cache.put("a", "1");
        cache.put("b", "2");
        cache.get("a");      // "a" is now the most-recently-used
        cache.put("c", "3"); // exceeds size 2 -> evicts the LRU entry, which is "b"

        assertEquals(2, cache.size());
        assertTrue(cache.get("a").isPresent());
        assertTrue(cache.get("c").isPresent());
        assertTrue(cache.get("b").isEmpty(), "the least-recently-used entry should be evicted");
    }

    @Test
    void rejectsInvalidTtl() {
        assertThrows(IllegalArgumentException.class, () -> new MemoryCache<>(null));
        assertThrows(IllegalArgumentException.class, () -> new MemoryCache<>(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new MemoryCache<>(Duration.ofSeconds(-1)));
    }

    @Test
    void rejectsInvalidMaxSize() {
        assertThrows(IllegalArgumentException.class, () -> new MemoryCache<>(Duration.ofMinutes(1), 0));
        assertThrows(IllegalArgumentException.class, () -> new MemoryCache<>(Duration.ofMinutes(1), -3));
    }
}
