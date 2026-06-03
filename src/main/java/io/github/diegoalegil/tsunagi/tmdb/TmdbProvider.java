package io.github.diegoalegil.tsunagi.tmdb;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * A streaming provider (e.g. Crunchyroll). {@code logoPath} is the raw relative
 * path TMDb returns; building the full logo URL is left to the caller.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TmdbProvider(
        Integer providerId,
        String providerName,
        String logoPath,
        Integer displayPriority
) {
}
