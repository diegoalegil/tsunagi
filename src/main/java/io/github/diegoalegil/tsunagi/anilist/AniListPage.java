package io.github.diegoalegil.tsunagi.anilist;

import java.util.List;

/** A page of the paginated popular-anime query, holding the media list. */
record AniListPage(List<AniListMedia> media) {
}
