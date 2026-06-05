package io.github.diegoalegil.tsunagi.tmdb;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The watch providers for one country, split by monetization type. All four
 * buckets are exposed unfiltered; deciding which ones matter (e.g. flatrate and
 * free only) is the caller's policy.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TmdbCountryProviders(
        @Nullable String link,
        @Nullable List<TmdbProvider> flatrate,
        @Nullable List<TmdbProvider> free,
        @Nullable List<TmdbProvider> rent,
        @Nullable List<TmdbProvider> buy
) {
}
