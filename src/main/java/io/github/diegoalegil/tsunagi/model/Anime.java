package io.github.diegoalegil.tsunagi.model;

import java.util.List;

/**
 * The unified anime model returned by Tsunagi, independent of which source
 * (AniList, TMDb or Jikan) produced it.
 *
 * <p>{@code averageScore} is always normalised to a 0–100 scale. {@code genres}
 * is never null (it defaults to an empty, immutable list). Any field that a
 * source does not provide is {@code null} (or empty for genres).
 *
 * @param id           a source-prefixed identifier, e.g. {@code "anilist:1"}
 * @param title        the preferred title
 * @param year         the release year, or null if unknown
 * @param description  a synopsis, or null
 * @param imageUrl     a cover/poster URL, or null
 * @param averageScore the average score on a 0–100 scale, or null if unrated
 * @param genres       the genres, never null (empty when unknown)
 * @param episodes     the number of episodes, or null
 * @param status       the airing status as reported by the source, or null
 * @param source       the source that produced this result, e.g. {@code "AniList"}
 */
public record Anime(
        String id,
        String title,
        Integer year,
        String description,
        String imageUrl,
        Double averageScore,
        List<String> genres,
        Integer episodes,
        String status,
        String source
) {
    public Anime {
        genres = (genres == null) ? List.of() : List.copyOf(genres);
    }
}
