package io.github.diegoalegil.tsunagi.tmdb;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A single result from TMDb search. Supports both {@code /search/tv} and
 * {@code /search/multi}: TV uses {@code name}/{@code first_air_date}, movies use
 * {@code title}/{@code release_date}, and {@code media_type} (populated only by
 * {@code /search/multi}) tells them apart. The {@code display*} and
 * {@code isTv()}/{@code isMovie()} helpers return the right value per type.
 *
 * <p>{@code posterPath} is the raw relative path TMDb returns; building a full
 * URL is left to the caller.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TmdbSearchResult(
        @Nullable Long id,
        @Nullable String name,
        @Nullable String originalName,
        @Nullable String overview,
        @Nullable String firstAirDate,
        @Nullable List<String> originCountry,
        @Nullable String posterPath,
        @Nullable Double popularity,
        @Nullable String title,
        @Nullable String originalTitle,
        @Nullable String releaseDate,
        @Nullable String mediaType,
        @Nullable String originalLanguage
) {

    /** Display title: a TV result uses {@code name}, a movie uses {@code title}. */
    public @Nullable String displayName() {
        return name != null ? name : title;
    }

    /** Original title: a TV result uses {@code original_name}, a movie uses {@code original_title}. */
    public @Nullable String displayOriginalName() {
        return originalName != null ? originalName : originalTitle;
    }

    /** Release date: a TV result uses {@code first_air_date}, a movie uses {@code release_date}. */
    public @Nullable String displayDate() {
        return firstAirDate != null ? firstAirDate : releaseDate;
    }

    /** A {@code /search/tv} result carries no media_type, so treat it as TV. */
    public boolean isTv() {
        return mediaType == null || "tv".equalsIgnoreCase(mediaType);
    }

    public boolean isMovie() {
        return "movie".equalsIgnoreCase(mediaType);
    }
}
