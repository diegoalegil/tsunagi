package io.github.diegoalegil.tsunagi.tmdb;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * The watch providers for one country, split by monetization type. All four
 * buckets are exposed unfiltered; deciding which ones matter (e.g. flatrate and
 * free only) is the caller's policy.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TmdbCountryProviders(
        String link,
        List<TmdbProvider> flatrate,
        List<TmdbProvider> free,
        List<TmdbProvider> rent,
        List<TmdbProvider> buy
) {
}
