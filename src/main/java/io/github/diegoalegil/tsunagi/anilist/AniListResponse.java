package io.github.diegoalegil.tsunagi.anilist;

import org.jspecify.annotations.Nullable;

/**
 * Top-level wrapper for the GraphQL {@code data} envelope. Package-private: it
 * exists only so {@code readValue} can deserialize the popular-anime response;
 * callers receive the unwrapped {@link AniListMedia} list.
 */
record AniListResponse(@Nullable AniListData data) {
}
