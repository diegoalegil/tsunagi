package io.github.diegoalegil.tsunagi.anilist;

import org.jspecify.annotations.Nullable;

/**
 * An AniList "fuzzy" date: any of year, month or day may be {@code null} when
 * the API only knows part of the date (e.g. a season with no announced day).
 */
public record AniListFuzzyDate(@Nullable Integer year, @Nullable Integer month, @Nullable Integer day) {
}
