package io.github.diegoalegil.tsunagi.anilist;

/**
 * An edge in the characters connection: the character's {@code role} (e.g.
 * {@code MAIN}) paired with the character {@code node} itself.
 */
public record AniListCharacterEdge(String role, AniListCharacter node) {
}
