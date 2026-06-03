package io.github.diegoalegil.tsunagi.anilist;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code data} object of the GraphQL response. The {@code Page} field is
 * capitalised in the API, so it is bound explicitly via {@link JsonProperty}.
 */
record AniListData(@JsonProperty("Page") AniListPage page) {
}
