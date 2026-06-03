package io.github.diegoalegil.tsunagi.tmdb;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * A single TV result from {@code /search/tv}. {@code posterPath} is the raw
 * relative path TMDb returns; turning it into a full URL is left to the caller.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TmdbSearchResult(
        Long id,
        String name,
        String originalName,
        String overview,
        String firstAirDate,
        List<String> originCountry,
        String posterPath,
        Double popularity
) {
}
