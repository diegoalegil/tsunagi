package io.github.diegoalegil.tsunagi.jikan;

import io.github.diegoalegil.tsunagi.exception.ApiException;
import io.github.diegoalegil.tsunagi.exception.RateLimitException;
import io.github.diegoalegil.tsunagi.exception.SourceUnavailableException;
import io.github.diegoalegil.tsunagi.exception.TsunagiException;
import io.github.diegoalegil.tsunagi.http.RetryPolicy;
import io.github.diegoalegil.tsunagi.model.Anime;
import io.github.diegoalegil.tsunagi.ratelimit.TokenBucketRateLimiter;
import io.github.diegoalegil.tsunagi.source.AnimeSource;

import org.jspecify.annotations.Nullable;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

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

    private static final String DEFAULT_SEARCH_URL = "https://api.jikan.moe/v4/anime";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TokenBucketRateLimiter rateLimiter;
    private final @Nullable RetryPolicy retryPolicy;
    private final Duration requestTimeout;
    // The search endpoint. Configurable only through a package-private constructor
    // so tests can target a local server; public constructors use the real host.
    private final String searchUrl;

    /** Creates a client with its own 3-requests-per-second rate limiter. */
    public JikanClient() {
        this(new TokenBucketRateLimiter(3, Duration.ofSeconds(1)), DEFAULT_REQUEST_TIMEOUT);
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
        this(rateLimiter, DEFAULT_REQUEST_TIMEOUT);
    }

    /** Creates a client with a shared rate limiter and a custom request timeout. */
    public JikanClient(TokenBucketRateLimiter rateLimiter, Duration requestTimeout) {
        this(rateLimiter, null, requestTimeout);
    }

    /**
     * Creates a client with a shared rate limiter and an optional retry policy.
     * The retry policy lets transient failures (5xx, rate limits, network blips)
     * be retried with backoff, just like the AniList and TMDb clients.
     */
    public JikanClient(TokenBucketRateLimiter rateLimiter, @Nullable RetryPolicy retryPolicy) {
        this(rateLimiter, retryPolicy, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Canonical constructor.
     *
     * @param rateLimiter    the rate limiter honoured before every request; required
     * @param retryPolicy    optional retry policy wrapping each search; may be null
     * @param requestTimeout per-request HTTP timeout; must be positive
     */
    public JikanClient(TokenBucketRateLimiter rateLimiter, @Nullable RetryPolicy retryPolicy, Duration requestTimeout) {
        this(rateLimiter, retryPolicy, requestTimeout, DEFAULT_SEARCH_URL);
    }

    /** Package-private test seam: lets tests point the client at a local server. */
    JikanClient(TokenBucketRateLimiter rateLimiter, @Nullable RetryPolicy retryPolicy, Duration requestTimeout, String searchUrl) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        this.objectMapper = new ObjectMapper();
        this.rateLimiter = rateLimiter;
        this.retryPolicy = retryPolicy;
        this.requestTimeout = requireTimeout(requestTimeout);
        this.searchUrl = searchUrl;
    }

    private static Duration requireTimeout(Duration requestTimeout) {
        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("requestTimeout must be positive, got " + requestTimeout);
        }
        return requestTimeout;
    }

    /**
     * Searches MyAnimeList (via Jikan) for an anime by title and returns the first
     * match, or an empty result when nothing matches.
     */
    @Override
    public Optional<Anime> searchAnime(String title) {
        AnimeSource.requireSearchTitle(title);
        String query = URLEncoder.encode(title, StandardCharsets.UTF_8);
        URI uri = URI.create(searchUrl + "?q=" + query + "&limit=1");

        // Each attempt re-acquires a rate-limiter token, so retries also stay
        // within the 3-requests-per-second budget. Parsing happens outside the
        // retry policy: a malformed body is permanent, not transient.
        String body = withPolicies(() -> fetchBody(uri));
        try {
            return parseFirstResult(body);
        } catch (JsonProcessingException e) {
            throw new TsunagiException("Failed to parse Jikan response", e);
        }
    }

    private String fetchBody(URI uri) {
        acquire();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
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
        return response.body();
    }

    /** Runs an operation under the retry policy when one is configured. */
    private <T> T withPolicies(Supplier<T> operation) {
        return (retryPolicy != null) ? retryPolicy.execute(operation) : operation.get();
    }

    /** Blocks until the rate limiter grants a token, so we never exceed 3 req/s. */
    private void acquire() {
        try {
            rateLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SourceUnavailableException("Jikan", "interrupted while rate limiting", e);
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

        // A result without a usable numeric mal_id is malformed; treat it as no
        // result instead of crashing on a missing/non-numeric field.
        JsonNode idNode = media.path("mal_id");
        if (!idNode.canConvertToInt()) {
            return Optional.empty();
        }
        String id = "jikan:" + idNode.asInt();

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

        List<String> genres = parseGenreNames(media.path("genres"));
        Integer episodes = intOrNull(media.path("episodes"));
        String status = textOrNull(media.path("status"));

        return Optional.of(new Anime(
                id, title, year, description, imageUrl, averageScore,
                genres, episodes, status, "Jikan"));
    }

    /** Reads the "name" of each object in a Jikan genres-style array. */
    private List<String> parseGenreNames(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (JsonNode element : node) {
            String name = textOrNull(element.path("name"));
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    private @Nullable Integer intOrNull(JsonNode node) {
        return node.isInt() ? node.asInt() : null;
    }

    /** Reads the top-level "year", falling back to aired.prop.from.year. */
    private @Nullable Integer readYear(JsonNode media) {
        JsonNode yearNode = media.path("year");
        if (yearNode.isInt()) {
            return yearNode.asInt();
        }
        JsonNode airedYear = media.path("aired").path("prop").path("from").path("year");
        return airedYear.isInt() ? airedYear.asInt() : null;
    }

    private @Nullable String textOrNull(JsonNode node) {
        return (node.isMissingNode() || node.isNull()) ? null : node.asText();
    }

    private @Nullable String firstNonNullText(JsonNode... nodes) {
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
