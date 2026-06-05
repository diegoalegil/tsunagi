package io.github.diegoalegil.tsunagi.tmdb;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.jspecify.annotations.Nullable;

/**
 * A single video entry. {@code key} is the provider-specific id (e.g. a YouTube
 * video id when {@code site} is {@code "YouTube"} and {@code type} is
 * {@code "Trailer"}).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TmdbVideo(
        @Nullable String key,
        @Nullable String site,
        @Nullable String type,
        @Nullable String name
) {
}
