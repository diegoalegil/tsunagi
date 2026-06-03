package io.github.diegoalegil.tsunagi.source;

import io.github.diegoalegil.tsunagi.exception.TsunagiException;
import io.github.diegoalegil.tsunagi.model.Anime;

import java.util.Optional;

/**
 * A single provider of anime data (AniList, TMDb, Jikan, ...).
 *
 * <p>Every source maps its own API response to the unified {@link Anime} model
 * and returns an empty result when nothing matches. This abstraction lets
 * {@code TsunagiClient} orchestrate the sources without knowing their details,
 * and lets tests inject fake sources.
 */
public interface AnimeSource {

    /**
     * Searches this source for an anime by title.
     *
     * @return the first match, or an empty optional when nothing matches
     * @throws IllegalArgumentException if {@code title} is null or blank
     * @throws TsunagiException if the source fails or cannot be reached
     */
    Optional<Anime> searchAnime(String title);

    /**
     * Validates a search title, returning it unchanged when valid.
     *
     * @throws IllegalArgumentException if {@code title} is null or blank
     */
    static String requireSearchTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("search title must not be null or blank");
        }
        return title;
    }
}
