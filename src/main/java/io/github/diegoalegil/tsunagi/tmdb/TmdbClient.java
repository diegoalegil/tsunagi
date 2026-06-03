package io.github.diegoalegil.tsunagi.tmdb;

import io.github.diegoalegil.tsunagi.exception.ApiException;
import io.github.diegoalegil.tsunagi.exception.RateLimitException;
import io.github.diegoalegil.tsunagi.exception.SourceUnavailableException;
import io.github.diegoalegil.tsunagi.exception.TsunagiException;
import io.github.diegoalegil.tsunagi.http.RetryPolicy;
import io.github.diegoalegil.tsunagi.model.Anime;
import io.github.diegoalegil.tsunagi.ratelimit.TokenBucketRateLimiter;
import io.github.diegoalegil.tsunagi.source.AnimeSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
import java.util.function.Supplier;

/**
 * Client for <a href="https://www.themoviedb.org">TMDb</a>, a REST API that
 * authenticates with a Bearer token. TMDb is movie/TV oriented; within Tsunagi
 * it is mainly useful for posters and extra metadata, so this client searches
 * the {@code /search/tv} endpoint.
 *
 * <p>The Bearer token is a secret and is provided through the constructor; it is
 * never logged or hardcoded.
 *
 * <p>Beyond the unified {@link #searchAnime} mapping, this client exposes the
 * raw TMDb endpoints DondeAnime-style consumers need: {@link #searchTv},
 * {@link #getTvDetails}, {@link #getWatchProviders} and {@link #getTrailers}.
 * The language is always a caller-supplied parameter — no implicit locale.
 */
public final class TmdbClient implements AnimeSource {

    private static final String API_BASE = "https://api.themoviedb.org/3";
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

    // All three are optional (nullable). When set, the rich endpoints (via get())
    // apply them; searchAnime is left untouched and uses none of them.
    private final String userAgent;
    private final RetryPolicy retryPolicy;
    private final TokenBucketRateLimiter rateLimiter;

    /**
     * Creates a client authenticated with the given TMDb v4 Bearer token and the
     * default request timeout.
     *
     * @param bearerToken the TMDb read access token; must not be null or blank
     */
    public TmdbClient(String bearerToken) {
        this(bearerToken, DEFAULT_REQUEST_TIMEOUT);
    }

    /** Creates a client with a custom per-request timeout and no extras. */
    public TmdbClient(String bearerToken, Duration requestTimeout) {
        this(bearerToken, requestTimeout, null, null, null);
    }

    /**
     * Canonical constructor.
     *
     * @param bearerToken    the TMDb read access token; must not be null or blank
     * @param requestTimeout per-request HTTP timeout; must be positive
     * @param userAgent      optional {@code User-Agent} for the rich endpoints;
     *                       only sent when non-null (requires the JVM flag
     *                       {@code -Djdk.httpclient.allowRestrictedHeaders=user-agent})
     * @param retryPolicy    optional retry policy wrapping each rich request
     * @param rateLimiter    optional rate limiter applied before each rich request
     */
    public TmdbClient(String bearerToken,
                      Duration requestTimeout,
                      String userAgent,
                      RetryPolicy retryPolicy,
                      TokenBucketRateLimiter rateLimiter) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new IllegalArgumentException("TMDb bearer token must not be null or blank");
        }
        this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        // TMDb returns many more fields than the records model, so do not fail on
        // unknown properties. This does not affect parseFirstResult, which uses
        // readTree/JsonNode rather than readValue.
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.bearerToken = bearerToken;
        this.requestTimeout = requireTimeout(requestTimeout);
        this.userAgent = userAgent;
        this.retryPolicy = retryPolicy;
        this.rateLimiter = rateLimiter;
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

    /**
     * Searches the {@code /search/tv} endpoint. {@code include_adult} is always
     * false. When {@code language} is null or blank it is simply omitted (TMDb
     * then uses its default), leaving locale choice to the caller.
     */
    public TmdbSearchResponse searchTv(String query, String language) {
        StringBuilder url = new StringBuilder(SEARCH_URL)
                .append("?query=").append(URLEncoder.encode(query, StandardCharsets.UTF_8))
                .append("&include_adult=false");
        appendLanguage(url, language);
        return get(url.toString(), TmdbSearchResponse.class);
    }

    /**
     * Searches the {@code /search/multi} endpoint (TV + movies + people). Each
     * result carries {@code media_type}; filter with {@link TmdbSearchResult#isTv()}
     * or {@link TmdbSearchResult#isMovie()}. {@code include_adult} is always false;
     * {@code language} is omitted when null/blank.
     */
    public TmdbSearchResponse searchMulti(String query, String language) {
        StringBuilder url = new StringBuilder(API_BASE)
                .append("/search/multi?query=").append(URLEncoder.encode(query, StandardCharsets.UTF_8))
                .append("&include_adult=false");
        appendLanguage(url, language);
        return get(url.toString(), TmdbSearchResponse.class);
    }

    /** Fetches {@code /tv/{id}} details (used for the localized overview). */
    public TmdbTvDetailsResponse getTvDetails(Long id, String language) {
        StringBuilder url = new StringBuilder(API_BASE).append("/tv/").append(id);
        appendLanguage(url, language);
        return get(url.toString(), TmdbTvDetailsResponse.class);
    }

    /** Fetches {@code /tv/{id}/watch/providers} (providers keyed by country). */
    public TmdbProvidersResponse getWatchProviders(Long id) {
        return get(API_BASE + "/tv/" + id + "/watch/providers", TmdbProvidersResponse.class);
    }

    /** Fetches {@code /tv/{id}/videos} in the requested language, if any. */
    public TmdbVideosResponse getTrailers(Long id, String language) {
        StringBuilder url = new StringBuilder(API_BASE).append("/tv/").append(id).append("/videos");
        appendLanguage(url, language);
        return get(url.toString(), TmdbVideosResponse.class);
    }

    /** Appends {@code language} as a query param, choosing {@code ?} or {@code &}. */
    private static void appendLanguage(StringBuilder url, String language) {
        if (language == null || language.isBlank()) {
            return;
        }
        char separator = url.indexOf("?") >= 0 ? '&' : '?';
        url.append(separator).append("language=")
                .append(URLEncoder.encode(language, StandardCharsets.UTF_8));
    }

    /**
     * Performs a GET against TMDb and deserializes the body into {@code type},
     * applying the rate limiter and retry policy when configured.
     */
    private <T> T get(String url, Class<T> type) {
        return withPolicies(() -> doGet(url, type));
    }

    private <T> T doGet(String url, Class<T> type) {
        acquire();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .GET();
        applyCommonHeaders(builder);

        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
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
            return objectMapper.readValue(response.body(), type);
        } catch (JsonProcessingException e) {
            throw new TsunagiException("Failed to parse TMDb response", e);
        }
    }

    /** Adds the Bearer auth and Accept headers, plus User-Agent when configured. */
    private void applyCommonHeaders(HttpRequest.Builder builder) {
        builder.header("Authorization", "Bearer " + bearerToken);
        builder.header("Accept", "application/json");
        if (userAgent != null) {
            builder.header("User-Agent", userAgent);
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

    /** Deserializes a {@code /search/tv} body (canned-JSON tests). */
    TmdbSearchResponse parseSearch(String body) throws JsonProcessingException {
        return objectMapper.readValue(body, TmdbSearchResponse.class);
    }

    /** Deserializes a {@code /tv/{id}} body (canned-JSON tests). */
    TmdbTvDetailsResponse parseTvDetails(String body) throws JsonProcessingException {
        return objectMapper.readValue(body, TmdbTvDetailsResponse.class);
    }

    /** Deserializes a {@code /tv/{id}/watch/providers} body (canned-JSON tests). */
    TmdbProvidersResponse parseProviders(String body) throws JsonProcessingException {
        return objectMapper.readValue(body, TmdbProvidersResponse.class);
    }

    /** Deserializes a {@code /tv/{id}/videos} body (canned-JSON tests). */
    TmdbVideosResponse parseVideos(String body) throws JsonProcessingException {
        return objectMapper.readValue(body, TmdbVideosResponse.class);
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

    /** Runs an operation under the retry policy when one is configured. */
    private <T> T withPolicies(Supplier<T> operation) {
        return (retryPolicy != null) ? retryPolicy.execute(operation) : operation.get();
    }

    /** Blocks for a rate-limiter permit when one is configured. */
    private void acquire() {
        if (rateLimiter == null) {
            return;
        }
        try {
            rateLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SourceUnavailableException("TMDb", "interrupted while rate limiting", e);
        }
    }
}
