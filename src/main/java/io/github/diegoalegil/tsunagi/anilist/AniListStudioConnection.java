package io.github.diegoalegil.tsunagi.anilist;

import java.util.List;

/** The {@code studios} connection of a media entry, wrapping the studio nodes. */
public record AniListStudioConnection(List<AniListStudio> nodes) {
}
