package io.github.diegoalegil.tsunagi;

import io.github.diegoalegil.tsunagi.anilist.AniListClient;
import io.github.diegoalegil.tsunagi.cache.MemoryCache;
import io.github.diegoalegil.tsunagi.exception.TsunagiException;
import io.github.diegoalegil.tsunagi.http.RetryPolicy;
import io.github.diegoalegil.tsunagi.jikan.JikanClient;
import io.github.diegoalegil.tsunagi.model.Anime;
import io.github.diegoalegil.tsunagi.source.AnimeSource;
import io.github.diegoalegil.tsunagi.tmdb.TmdbClient;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * The unified entry point of Tsunagi. Hides AniList, TMDb and Jikan behind a
 * single {@code searchAnime} call that returns a unified {@link Anime}.
 *
 * <pre>{@code
 * TsunagiConfig config = TsunagiConfig.builder()
 *         .tmdbToken(System.getenv("TMDB_TOKEN"))
 *         .cacheEnabled(true)
 *         .build();
 *
 * TsunagiClient tsunagi = new TsunagiClient(config);
 * Optional<Anime> anime = tsunagi.searchAnime("Cowboy Bebop");
 * }</pre>
 *
 * <p>Search strategy:
 * <ol>
 *   <li>If the cache is enabled and holds this title (including a remembered
 *       "no result"), return it.</li>
 *   <li>Query AniList as the primary source.</li>
 *   <li>If AniList has a result but some fields are missing and a TMDb token is
 *       configured, fill the gaps from TMDb (best-effort: a TMDb failure never
 *       fails the whole search).</li>
 *   <li>If AniList has no result, fall back to Jikan.</li>
 * </ol>
 *
 * <p>When retries are enabled, every individual source call is retried on
 * transient failures with exponential backoff.
 */
public final class TsunagiClient {

    private final AnimeSource primary;   // AniList
    private final @Nullable AnimeSource enricher;  // TMDb, may be null when no token is configured
    private final AnimeSource fallback;  // Jikan
    private final @Nullable MemoryCache<String, Optional<Anime>> cache; // null when caching is disabled
    private final @Nullable RetryPolicy retryPolicy; // null when retries are disabled

    /** Builds a client with the real AniList, TMDb (if a token is set) and Jikan sources. */
    public TsunagiClient(TsunagiConfig config) {
        this(
                new AniListClient(config.requestTimeout(), config.userAgent().orElse(null), null, null),
                config.tmdbToken()
                        .map(token -> (AnimeSource) new TmdbClient(
                                token, config.requestTimeout(), config.userAgent().orElse(null), null, null))
                        .orElse(null),
                new JikanClient(config.requestTimeout()),
                config.cacheEnabled() ? new MemoryCache<>(config.cacheTtl(), config.cacheMaxSize()) : null,
                config.retryEnabled()
                        ? RetryPolicy.exponentialBackoff(config.retryMaxAttempts(), config.retryInitialDelay())
                        : null);
    }

    /** Package-private constructor used by tests to inject fake sources (no cache, no retry). */
    TsunagiClient(AnimeSource primary, @Nullable AnimeSource enricher, AnimeSource fallback) {
        this(primary, enricher, fallback, null, null);
    }

    /** Package-private constructor used by tests to inject every collaborator. */
    TsunagiClient(AnimeSource primary,
                  @Nullable AnimeSource enricher,
                  AnimeSource fallback,
                  @Nullable MemoryCache<String, Optional<Anime>> cache,
                  @Nullable RetryPolicy retryPolicy) {
        this.primary = primary;
        this.enricher = enricher;
        this.fallback = fallback;
        this.cache = cache;
        this.retryPolicy = retryPolicy;
    }

    /**
     * Searches for an anime by title across the configured sources.
     *
     * @return the unified result, or an empty optional when no source matches
     */
    public Optional<Anime> searchAnime(String title) {
        AnimeSource.requireSearchTitle(title);

        if (cache != null) {
            Optional<Optional<Anime>> cached = cache.get(title);
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        Optional<Anime> result = doSearch(title);

        if (cache != null) {
            cache.put(title, result);
        }
        return result;
    }

    private Optional<Anime> doSearch(String title) {
        Optional<Anime> base = query(primary, title);
        if (base.isPresent()) {
            return Optional.of(enrich(base.get(), title));
        }
        return query(fallback, title);
    }

    /** Calls a source, wrapping it in the retry policy when one is configured. */
    private Optional<Anime> query(AnimeSource source, String title) {
        if (retryPolicy == null) {
            return source.searchAnime(title);
        }
        return retryPolicy.execute(() -> source.searchAnime(title));
    }

    /** Fills missing fields of the AniList result from TMDb, when worthwhile. */
    private Anime enrich(Anime base, String title) {
        if (enricher == null || isComplete(base)) {
            return base;
        }
        try {
            Optional<Anime> extra = query(enricher, title);
            return extra.map(found -> merge(base, found)).orElse(base);
        } catch (TsunagiException e) {
            // Enrichment is best-effort: keep the AniList result if TMDb fails.
            return base;
        }
    }

    private boolean isComplete(Anime anime) {
        return anime.title() != null
                && anime.year() != null
                && anime.description() != null
                && anime.imageUrl() != null
                && anime.averageScore() != null;
    }

    /** Keeps the base (AniList) values and fills only the missing ones from {@code extra}. */
    private Anime merge(Anime base, Anime extra) {
        return new Anime(
                base.id(),
                base.title() != null ? base.title() : extra.title(),
                base.year() != null ? base.year() : extra.year(),
                base.description() != null ? base.description() : extra.description(),
                base.imageUrl() != null ? base.imageUrl() : extra.imageUrl(),
                base.averageScore() != null ? base.averageScore() : extra.averageScore(),
                !base.genres().isEmpty() ? base.genres() : extra.genres(),
                base.episodes() != null ? base.episodes() : extra.episodes(),
                base.status() != null ? base.status() : extra.status(),
                base.source());
    }
}
