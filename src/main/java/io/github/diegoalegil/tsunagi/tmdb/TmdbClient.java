package io.github.diegoalegil.tsunagi.tmdb;

import io.github.diegoalegil.tsunagi.exception.ApiException;
import io.github.diegoalegil.tsunagi.exception.RateLimitException;
import io.github.diegoalegil.tsunagi.exception.SourceUnavailableException;
import io.github.diegoalegil.tsunagi.exception.TsunagiException;
import io.github.diegoalegil.tsunagi.model.Anime;
import io.github.diegoalegil.tsunagi.source.AnimeSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Client for <a href="https://www.themoviedb.org">TMDb</a>, a REST API that
 * authenticates with a Bearer token. TMDb is movie/TV oriented; within Tsunagi
 * it is mainly useful for posters and extra metadata, so this client searches
 * the {@code /search/tv} endpoint.
 *
 * <p>The Bearer token is a secret and is provided through the constructor; it is
 * never logged or hardcoded.
 */
public final class TmdbClient implements AnimeSource {

    private static final String SEARCH_URL = "https://api.themoviedb.org/3/search/tv";

    // Standard TMDb image CDN base plus a reasonable poster size. TMDb also
    // exposes a /configuration endpoint with the canonical base, but this prefix
    // is stable and avoids an extra request per lookup.
    private static final String POSTER_BASE = "https://image.tmdb.org/t/p/w500";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String bearerToken;
    private final Duration requestTimeout;

    /**
     * Creates a client authenticated with the given TMDb v4 Bearer token and the
     * default request timeout.
     *
     * @param bearerToken the TMDb read access token; must not be null or blank
     */
    public TmdbClient(String bearerToken) {
        this(bearerToken, DEFAULT_REQUEST_TIMEOUT);
    }

    /** Creates a client with a custom per-request timeout. */
    public TmdbClient(String bearerToken, Duration requestTimeout) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new IllegalArgumentException("TMDb bearer token must not be null or blank");
        }
        this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        this.objectMapper = new ObjectMapper();
        this.bearerToken = bearerToken;
        this.requestTimeout = requireTimeout(requestTimeout);
    }

    private static Duration requireTimeout(Duration requestTimeout) {
        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("requestTimeout must be positive, got " + requestTimeout);
        }
        return requestTimeout;
    }

    /**
     * Searches TMDb for a TV show by title and returns the first match, or an
     * empty result when nothing matches.
     */
    @Override
    public Optional<Anime> searchAnime(String title) {
        AnimeSource.requireSearchTitle(title);
        String query = URLEncoder.encode(title, StandardCharsets.UTF_8);
        URI uri = URI.create(SEARCH_URL + "?query=" + query);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + bearerToken)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new SourceUnavailableException("TMDb", "request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SourceUnavailableException("TMDb", "request interrupted", e);
        }

        int status = response.statusCode();
        if (status == 429) {
            throw new RateLimitException("TMDb");
        }
        if (status != 200) {
            throw new ApiException("TMDb", status);
        }

        try {
            return parseFirstResult(response.body());
        } catch (JsonProcessingException e) {
            throw new TsunagiException("Failed to parse TMDb response", e);
        }
    }

    Optional<Anime> parseFirstResult(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode results = root.path("results");

        // TMDb returns 200 with an empty "results" array when nothing matches.
        if (!results.isArray() || results.isEmpty()) {
            return Optional.empty();
        }

        JsonNode media = results.get(0);

        String id = "tmdb:" + media.get("id").asInt();

        String title = firstNonNullText(
                media.path("name"),
                media.path("original_name"));

        Integer year = readYear(media.path("first_air_date"));

        String description = textOrNull(media.path("overview"));

        String imageUrl = readPosterUrl(media.path("poster_path"));

        // TMDb vote_average is on a 0-10 scale; normalise to the unified 0-100 scale.
        Double averageScore = null;
        JsonNode voteNode = media.path("vote_average");
        if (voteNode.isNumber() && voteNode.asDouble() > 0.0) {
            averageScore = voteNode.asDouble() * 10.0;
        }

        // The /search/tv endpoint does not include genres (only numeric
        // genre_ids), episode counts or status, so those are left empty/null.
        // TMDb's role in Tsunagi is posters and scores.
        return Optional.of(new Anime(
                id, title, year, description, imageUrl, averageScore,
                List.of(), null, null, "TMDb"));
    }

    /** Extracts the year from a "YYYY-MM-DD" first air date, or null if absent. */
    private Integer readYear(JsonNode firstAirDate) {
        if (firstAirDate.isMissingNode() || firstAirDate.isNull()) {
            return null;
        }
        String date = firstAirDate.asText();
        if (date.length() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(date.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Turns a "/poster.jpg" path into a full CDN URL, or null when absent. */
    private String readPosterUrl(JsonNode posterPath) {
        if (posterPath.isMissingNode() || posterPath.isNull()) {
            return null;
        }
        String path = posterPath.asText();
        return path.isEmpty() ? null : POSTER_BASE + path;
    }

    private String textOrNull(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return text.isEmpty() ? null : text;
    }

    private String firstNonNullText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && !node.isMissingNode() && !node.isNull()) {
                String text = node.asText();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return null;
    }
}
