package io.github.diegoalegil.tsunagi.anilist;

import org.jspecify.annotations.Nullable;

/** The large and medium image URLs of a character. */
public record AniListCharacterImage(@Nullable String large, @Nullable String medium) {
}
