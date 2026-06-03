package io.github.diegoalegil.tsunagi.anilist;

/**
 * An AniList "fuzzy" date: any of year, month or day may be {@code null} when
 * the API only knows part of the date (e.g. a season with no announced day).
 */
public record AniListFuzzyDate(Integer year, Integer month, Integer day) {
}
