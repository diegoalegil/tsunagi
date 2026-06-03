package io.github.diegoalegil.tsunagi.jikan;

import io.github.diegoalegil.tsunagi.exception.ApiException;
import io.github.diegoalegil.tsunagi.exception.RateLimitException;
import io.github.diegoalegil.tsunagi.exception.SourceUnavailableException;
import io.github.diegoalegil.tsunagi.exception.TsunagiException;
import io.github.diegoalegil.tsunagi.http.HttpDefaults;
import io.github.diegoalegil.tsunagi.model.Anime;
import io.github.diegoalegil.tsunagi.ratelimit.TokenBucketRateLimiter;
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
import java.util.Optional;

/**
 * Client for the <a href="https://jikan.moe">Jikan</a> REST API, an unofficial
 * MyAnimeList API that needs no authentication but enforces a strict rate limit
 * of 3 requests per second.
 *
 * <p>Every request goes through a {@link TokenBucketRateLimiter} so the limit is
 * honoured. By default the client owns a 3-requests-per-second limiter, but one
 * can be supplied (and shared) through the constructor.
 */
public final class JikanClient implements AnimeSource {

    private static final String SEARCH_URL = "https://api.jikan.moe/v4/anime";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TokenBucketRateLimiter rateLimiter;
    private final Duration requestTimeout;

    /** Creates a client with its own 3-requests-per-second rate limiter. */
    public JikanClient() {
        this(new TokenBucketRateLimiter(3, Duration.ofSeconds(1)), HttpDefaults.REQUEST_TIMEOUT);
    }

    /** Creates a client with its own rate limiter and a custom request timeout. */
    public JikanClient(Duration requestTimeout) {
        this(new TokenBucketRateLimiter(3, Duration.ofSeconds(1)), requestTimeout);
    }

    /**
     * Creates a client that shares the given rate limiter. Useful when several
     * Jikan-backed components must respect a single global limit.
     */
    public JikanClient(TokenBucketRateLimiter rateLimiter) {
        this(rateLimiter, HttpDefaults.REQUEST_TIMEOUT);
    }

    /** Creates a client with a shared rate limiter and a custom request timeout. */
    public JikanClient(TokenBucketRateLimiter rateLimiter, Duration requestTimeout) {
        this.httpClient = HttpDefaults.newClient();
        this.objectMapper = new ObjectMapper();
        this.rateLimiter = rateLimiter;
        this.requestTimeout = HttpDefaults.validateRequestTimeout(requestTimeout);
    }

    /**
     * Searches MyAnimeList (via Jikan) for an anime by title and returns the first
     * match, or an empty result when nothing matches.
     */
    @Override
    public Optional<Anime> searchAnime(String title) {
        AnimeSource.requireSearchTitle(title);
        String query = URLEncoder.encode(title, StandardCharsets.UTF_8);
        URI uri = URI.create(SEARCH_URL + "?q=" + query + "&limit=1");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            // Block until the rate limiter grants a token, so we never exceed 3 req/s.
            rateLimiter.acquire();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new SourceUnavailableException("Jikan", "request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SourceUnavailableException("Jikan", "request interrupted", e);
        }

        int status = response.statusCode();
        if (status == 429) {
            throw new RateLimitException("Jikan");
        }
        if (status != 200) {
            throw new ApiException("Jikan", status);
        }

        try {
            return parseFirstResult(response.body());
        } catch (JsonProcessingException e) {
            throw new TsunagiException("Failed to parse Jikan response", e);
        }
    }

    Optional<Anime> parseFirstResult(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode data = root.path("data");

        // Jikan returns 200 with an empty "data" array when nothing matches.
        if (!data.isArray() || data.isEmpty()) {
            return Optional.empty();
        }

        JsonNode media = data.get(0);

        String id = "jikan:" + media.get("mal_id").asInt();

        String title = firstNonNullText(
                media.path("title"),
                media.path("title_english"),
                media.path("title_japanese"));

        Integer year = readYear(media);

        String description = textOrNull(media.path("synopsis"));

        String imageUrl = firstNonNullText(
                media.path("images").path("jpg").path("large_image_url"),
                media.path("images").path("jpg").path("image_url"));

        // Jikan scores are on a 0-10 scale; normalise to the 0-100 scale used by
        // the unified model (and by AniList) so averageScore is comparable.
        Double averageScore = null;
        JsonNode scoreNode = media.path("score");
        if (scoreNode.isNumber()) {
            averageScore = scoreNode.asDouble() * 10.0;
        }

        return Optional.of(new Anime(id, title, year, description, imageUrl, averageScore));
    }

    /** Reads the top-level "year", falling back to aired.prop.from.year. */
    private Integer readYear(JsonNode media) {
        JsonNode yearNode = media.path("year");
        if (yearNode.isInt()) {
            return yearNode.asInt();
        }
        JsonNode airedYear = media.path("aired").path("prop").path("from").path("year");
        return airedYear.isInt() ? airedYear.asInt() : null;
    }

    private String textOrNull(JsonNode node) {
        return (node.isMissingNode() || node.isNull()) ? null : node.asText();
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
