package io.github.diegoalegil.tsunagi.anilist;

import java.util.List;

/** The {@code characters} connection of a media entry, wrapping the edges. */
public record AniListCharacterConnection(List<AniListCharacterEdge> edges) {
}
