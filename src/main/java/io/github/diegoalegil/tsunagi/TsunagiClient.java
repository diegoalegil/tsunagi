package io.github.diegoalegil.tsunagi;

import io.github.diegoalegil.tsunagi.anilist.AniListClient;
import io.github.diegoalegil.tsunagi.exception.TsunagiException;
import io.github.diegoalegil.tsunagi.jikan.JikanClient;
import io.github.diegoalegil.tsunagi.model.Anime;
import io.github.diegoalegil.tsunagi.source.AnimeSource;
import io.github.diegoalegil.tsunagi.tmdb.TmdbClient;

import java.util.Optional;

/**
 * The unified entry point of Tsunagi. Hides AniList, TMDb and Jikan behind a
 * single {@code searchAnime} call that returns a unified {@link Anime}.
 *
 * <pre>{@code
 * TsunagiConfig config = TsunagiConfig.builder()
 *         .tmdbToken(System.getenv("TMDB_TOKEN"))
 *         .build();
 *
 * TsunagiClient tsunagi = new TsunagiClient(config);
 * Optional<Anime> anime = tsunagi.searchAnime("Cowboy Bebop");
 * }</pre>
 *
 * <p>Search strategy:
 * <ol>
 *   <li>Query AniList as the primary source.</li>
 *   <li>If AniList has a result but some fields are missing and a TMDb token is
 *       configured, fill the gaps from TMDb (best-effort: a TMDb failure never
 *       fails the whole search).</li>
 *   <li>If AniList has no result, fall back to Jikan.</li>
 * </ol>
 */
public final class TsunagiClient {

    private final AnimeSource primary;   // AniList
    private final AnimeSource enricher;  // TMDb, may be null when no token is configured
    private final AnimeSource fallback;  // Jikan

    /** Builds a client with the real AniList, TMDb (if a token is set) and Jikan sources. */
    public TsunagiClient(TsunagiConfig config) {
        this.primary = new AniListClient();
        this.fallback = new JikanClient();
        this.enricher = config.tmdbToken().map(token -> (AnimeSource) new TmdbClient(token)).orElse(null);
    }

    /**
     * Package-private constructor used by tests to inject fake sources.
     *
     * @param enricher may be null to disable enrichment
     */
    TsunagiClient(AnimeSource primary, AnimeSource enricher, AnimeSource fallback) {
        this.primary = primary;
        this.enricher = enricher;
        this.fallback = fallback;
    }

    /**
     * Searches for an anime by title across the configured sources.
     *
     * @return the unified result, or an empty optional when no source matches
     */
    public Optional<Anime> searchAnime(String title) {
        Optional<Anime> base = primary.searchAnime(title);
        if (base.isPresent()) {
            return Optional.of(enrich(base.get(), title));
        }
        return fallback.searchAnime(title);
    }

    /** Fills missing fields of the AniList result from TMDb, when worthwhile. */
    private Anime enrich(Anime base, String title) {
        if (enricher == null || isComplete(base)) {
            return base;
        }
        try {
            Optional<Anime> extra = enricher.searchAnime(title);
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

    /** Keeps the base (AniList) values and fills only the null ones from {@code extra}. */
    private Anime merge(Anime base, Anime extra) {
        return new Anime(
                base.id(),
                base.title() != null ? base.title() : extra.title(),
                base.year() != null ? base.year() : extra.year(),
                base.description() != null ? base.description() : extra.description(),
                base.imageUrl() != null ? base.imageUrl() : extra.imageUrl(),
                base.averageScore() != null ? base.averageScore() : extra.averageScore());
    }
}
