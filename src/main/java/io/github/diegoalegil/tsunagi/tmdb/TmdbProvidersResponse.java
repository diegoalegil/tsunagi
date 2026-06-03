package io.github.diegoalegil.tsunagi.tmdb;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;

/**
 * The response of {@code /tv/{id}/watch/providers}. {@code results} is keyed by
 * ISO country code (e.g. {@code "ES"}), each holding that country's providers.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TmdbProvidersResponse(
        Long id,
        Map<String, TmdbCountryProviders> results
) {
}
