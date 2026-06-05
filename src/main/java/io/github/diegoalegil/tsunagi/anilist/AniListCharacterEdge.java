package io.github.diegoalegil.tsunagi.anilist;

import org.jspecify.annotations.Nullable;

/**
 * An edge in the characters connection: the character's {@code role} (e.g.
 * {@code MAIN}) paired with the character {@code node} itself.
 */
public record AniListCharacterEdge(@Nullable String role, @Nullable AniListCharacter node) {
}
