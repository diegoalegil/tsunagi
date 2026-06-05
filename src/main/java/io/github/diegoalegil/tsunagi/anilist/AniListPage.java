package io.github.diegoalegil.tsunagi.anilist;

import org.jspecify.annotations.Nullable;

import java.util.List;

/** A page of the paginated popular-anime query, holding the media list. */
record AniListPage(@Nullable List<AniListMedia> media) {
}
