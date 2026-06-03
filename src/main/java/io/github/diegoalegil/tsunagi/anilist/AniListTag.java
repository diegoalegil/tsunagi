package io.github.diegoalegil.tsunagi.anilist;

/** A descriptive tag on a media entry, with its relevance {@code rank} (0–100). */
public record AniListTag(String name, Integer rank) {
}
