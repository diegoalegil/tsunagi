package io.github.diegoalegil.tsunagi.tmdb;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** The response of {@code /tv/{id}/videos}: the list of related videos. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TmdbVideosResponse(
        @Nullable Long id,
        @Nullable List<TmdbVideo> results
) {
}
