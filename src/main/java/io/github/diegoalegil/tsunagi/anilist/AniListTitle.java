package io.github.diegoalegil.tsunagi.anilist;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The romanised, English and native-language titles of an AniList media entry.
 * {@code native} is a Java keyword, so the native-language title is bound to
 * {@code nativeTitle} via {@link JsonProperty}.
 */
public record AniListTitle(String romaji, String english, @JsonProperty("native") String nativeTitle) {
}
