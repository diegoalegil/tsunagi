package io.github.diegoalegil.tsunagi.anilist;

import java.util.List;

/**
 * A single AniList {@code Media} entry as returned by the popular-anime query,
 * carrying the rich metadata (titles, dates, studios, characters, tags…) that
 * the lightweight {@code searchAnime} mapping does not expose.
 *
 * <p>Field names and order mirror the GraphQL selection set; any value the API
 * omits is deserialized as {@code null} (or an empty list).
 */
public record AniListMedia(
        Long id,
        AniListTitle title,
        AniListFuzzyDate startDate,
        AniListFuzzyDate endDate,
        Integer episodes,
        Integer duration,
        String format,
        String status,
        Integer averageScore,
        Integer popularity,
        String description,
        AniListCoverImage coverImage,
        String bannerImage,
        List<String> genres,
        List<String> synonyms,
        AniListStudioConnection studios,
        String season,
        Integer seasonYear,
        AniListCharacterConnection characters,
        List<AniListTag> tags
) {
}
