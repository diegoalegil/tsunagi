package io.github.diegoalegil.tsunagi.anilist;

/**
 * A studio credited on a media entry. {@code isAnimationStudio} distinguishes
 * the animation studio from mere producers in the same connection.
 */
public record AniListStudio(Long id, String name, Boolean isAnimationStudio) {
}
