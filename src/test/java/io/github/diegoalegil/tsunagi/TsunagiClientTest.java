package io.github.diegoalegil.tsunagi;

import io.github.diegoalegil.tsunagi.cache.MemoryCache;
import io.github.diegoalegil.tsunagi.exception.SourceUnavailableException;
import io.github.diegoalegil.tsunagi.exception.TsunagiException;
import io.github.diegoalegil.tsunagi.http.RetryPolicy;
import io.github.diegoalegil.tsunagi.model.Anime;
import io.github.diegoalegil.tsunagi.source.AnimeSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsunagiClientTest {

    /** A fake source that returns a canned result, or fails on demand. */
    private static final class FakeSource implements AnimeSource {
        private final Optional<Anime> result;
        private final TsunagiException failure;
        private final int failTimes;
        boolean called;
        int calls;

        private FakeSource(Optional<Anime> result, TsunagiException failure, int failTimes) {
            this.result = result;
            this.failure = failure;
            this.failTimes = failTimes;
        }

        static FakeSource returning(Anime anime) {
            return new FakeSource(Optional.of(anime), null, 0);
        }

        static FakeSource empty() {
            return new FakeSource(Optional.empty(), null, 0);
        }

        static FakeSource failing() {
            return new FakeSource(Optional.empty(), new TsunagiException("boom"), Integer.MAX_VALUE);
        }

        static FakeSource transientThenReturning(int failTimes, Anime anime) {
            TsunagiException transientError =
                    new SourceUnavailableException("Fake", "temporary", new IOException("down"));
            return new FakeSource(Optional.of(anime), transientError, failTimes);
        }

        @Override
        public Optional<Anime> searchAnime(String title) {
            called = true;
            calls++;
            if (calls <= failTimes) {
                throw failure;
            }
            return result;
        }
    }

    private static Anime complete(String id) {
        return new Anime(id, "Cowboy Bebop", 1998, "Bounty hunters.", "https://img/cb.jpg", 86.0);
    }

    @Test
    void returnsAniListResultWhenFound() throws Exception {
        FakeSource anilist = FakeSource.returning(complete("anilist:1"));
        FakeSource jikan = FakeSource.returning(complete("jikan:1"));
        TsunagiClient client = new TsunagiClient(anilist, null, jikan);

        Optional<Anime> result = client.searchAnime("Cowboy Bebop");

        assertTrue(result.isPresent());
        assertEquals("anilist:1", result.get().id());
        assertFalse(jikan.called, "Jikan should not be used when AniList has a result");
    }

    @Test
    void fallsBackToJikanWhenAniListIsEmpty() throws Exception {
        FakeSource anilist = FakeSource.empty();
        FakeSource jikan = FakeSource.returning(complete("jikan:7"));
        TsunagiClient client = new TsunagiClient(anilist, null, jikan);

        Optional<Anime> result = client.searchAnime("Obscure Show");

        assertTrue(result.isPresent());
        assertEquals("jikan:7", result.get().id());
        assertTrue(jikan.called);
    }

    @Test
    void returnsEmptyWhenNoSourceMatches() throws Exception {
        TsunagiClient client = new TsunagiClient(FakeSource.empty(), null, FakeSource.empty());

        assertTrue(client.searchAnime("Nothing").isEmpty());
    }

    @Test
    void fillsMissingImageFromTmdb() throws Exception {
        // AniList result is missing the image; everything else is present.
        Anime partial = new Anime("anilist:1", "Cowboy Bebop", 1998, "Bounty hunters.", null, 86.0);
        Anime tmdb = new Anime("tmdb:30991", "Cowboy Bebop TV", 1998, "TV overview.", "https://img/poster.jpg", 85.0);

        FakeSource anilist = FakeSource.returning(partial);
        FakeSource tmdbSource = FakeSource.returning(tmdb);
        TsunagiClient client = new TsunagiClient(anilist, tmdbSource, FakeSource.empty());

        Anime result = client.searchAnime("Cowboy Bebop").orElseThrow();

        assertEquals("anilist:1", result.id());           // identity stays AniList's
        assertEquals("Cowboy Bebop", result.title());     // existing field kept
        assertEquals("https://img/poster.jpg", result.imageUrl()); // gap filled from TMDb
        assertTrue(tmdbSource.called);
    }

    @Test
    void skipsTmdbWhenAniListResultIsComplete() throws Exception {
        FakeSource anilist = FakeSource.returning(complete("anilist:1"));
        FakeSource tmdb = FakeSource.failing(); // would throw if it were ever called
        TsunagiClient client = new TsunagiClient(anilist, tmdb, FakeSource.empty());

        Anime result = client.searchAnime("Cowboy Bebop").orElseThrow();

        assertEquals("anilist:1", result.id());
        assertFalse(tmdb.called, "TMDb should not be queried when the result is already complete");
    }

    @Test
    void cachesResultsAndDoesNotHitSourcesTwice() throws Exception {
        FakeSource anilist = FakeSource.returning(complete("anilist:1"));
        MemoryCache<String, Optional<Anime>> cache = new MemoryCache<>(Duration.ofMinutes(10));
        TsunagiClient client = new TsunagiClient(anilist, null, FakeSource.empty(), cache, null);

        Optional<Anime> first = client.searchAnime("Cowboy Bebop");
        Optional<Anime> second = client.searchAnime("Cowboy Bebop");

        assertEquals(first, second);
        assertEquals(1, anilist.calls, "the second search should be served from the cache");
    }

    @Test
    void retriesTransientSourceFailures() throws Exception {
        // AniList fails twice transiently, then returns on the third attempt.
        FakeSource anilist = FakeSource.transientThenReturning(2, complete("anilist:1"));
        RetryPolicy retry = RetryPolicy.exponentialBackoff(3, Duration.ofMillis(1));
        TsunagiClient client = new TsunagiClient(anilist, null, FakeSource.empty(), null, retry);

        Anime result = client.searchAnime("Cowboy Bebop").orElseThrow();

        assertEquals("anilist:1", result.id());
        assertEquals(3, anilist.calls);
    }

    @Test
    void toleratesTmdbFailureAndKeepsAniListResult() throws Exception {
        Anime partial = new Anime("anilist:1", "Cowboy Bebop", 1998, "Bounty hunters.", null, 86.0);
        FakeSource anilist = FakeSource.returning(partial);
        FakeSource tmdb = FakeSource.failing();
        TsunagiClient client = new TsunagiClient(anilist, tmdb, FakeSource.empty());

        Anime result = client.searchAnime("Cowboy Bebop").orElseThrow();

        assertEquals("anilist:1", result.id());
        assertTrue(tmdb.called);
    }
}
