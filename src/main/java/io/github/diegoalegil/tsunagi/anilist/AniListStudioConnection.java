package io.github.diegoalegil.tsunagi.anilist;

import org.jspecify.annotations.Nullable;

import java.util.List;

/** The {@code studios} connection of a media entry, wrapping the studio nodes. */
public record AniListStudioConnection(@Nullable List<AniListStudio> nodes) {
}
