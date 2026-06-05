package io.github.diegoalegil.tsunagi.anilist;

import org.jspecify.annotations.Nullable;

/**
 * A studio credited on a media entry. {@code isAnimationStudio} distinguishes
 * the animation studio from mere producers in the same connection.
 */
public record AniListStudio(@Nullable Long id, @Nullable String name, @Nullable Boolean isAnimationStudio) {
}
