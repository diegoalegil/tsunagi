package io.github.diegoalegil.tsunagi.anilist;

import org.jspecify.annotations.Nullable;

/** A character node: its id, name and image. */
public record AniListCharacter(@Nullable Long id, @Nullable AniListCharacterName name, @Nullable AniListCharacterImage image) {
}
