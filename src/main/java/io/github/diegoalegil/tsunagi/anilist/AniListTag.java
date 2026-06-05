package io.github.diegoalegil.tsunagi.anilist;

import org.jspecify.annotations.Nullable;

/** A descriptive tag on a media entry, with its relevance {@code rank} (0–100). */
public record AniListTag(@Nullable String name, @Nullable Integer rank) {
}
