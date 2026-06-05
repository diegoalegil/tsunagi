package io.github.diegoalegil.tsunagi.tmdb;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The paginated response of TMDb's {@code /search/tv} endpoint. TMDb uses
 * snake_case JSON keys, so the strategy maps e.g. {@code total_results} to
 * {@code totalResults}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TmdbSearchResponse(
        @Nullable Integer page,
        @Nullable List<TmdbSearchResult> results,
        @Nullable Integer totalResults,
        @Nullable Integer totalPages
) {
}
