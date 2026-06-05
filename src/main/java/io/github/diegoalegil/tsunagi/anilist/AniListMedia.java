package io.github.diegoalegil.tsunagi.anilist;

import org.jspecify.annotations.Nullable;

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
        @Nullable Long id,
        @Nullable AniListTitle title,
        @Nullable AniListFuzzyDate startDate,
        @Nullable AniListFuzzyDate endDate,
        @Nullable Integer episodes,
        @Nullable Integer duration,
        @Nullable String format,
        @Nullable String status,
        @Nullable Integer averageScore,
        @Nullable Integer popularity,
        @Nullable String description,
        @Nullable AniListCoverImage coverImage,
        @Nullable String bannerImage,
        @Nullable List<String> genres,
        @Nullable List<String> synonyms,
        @Nullable AniListStudioConnection studios,
        @Nullable String season,
        @Nullable Integer seasonYear,
        @Nullable AniListCharacterConnection characters,
        @Nullable List<AniListTag> tags
) {
}
