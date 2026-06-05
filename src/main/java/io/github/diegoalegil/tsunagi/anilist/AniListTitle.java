package io.github.diegoalegil.tsunagi.anilist;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * The romanised, English and native-language titles of an AniList media entry.
 * {@code native} is a Java keyword, so the native-language title is bound to
 * {@code nativeTitle} via {@link JsonProperty}.
 */
public record AniListTitle(@Nullable String romaji, @Nullable String english, @JsonProperty("native") @Nullable String nativeTitle) {
}
