package io.github.diegoalegil.tsunagi.tmdb;

/**
 * The slice of TMDb's {@code /tv/{id}} details response this client needs: just
 * the localized {@code overview}. No naming strategy is required for a single
 * lowercase field.
 */
public record TmdbTvDetailsResponse(String overview) {
}
