package io.github.diegoalegil.tsunagi.anilist;

import io.github.diegoalegil.tsunagi.exception.ApiException;
import io.github.diegoalegil.tsunagi.exception.RateLimitException;
import io.github.diegoalegil.tsunagi.exception.SourceUnavailableException;
import io.github.diegoalegil.tsunagi.exception.TsunagiException;
import io.github.diegoalegil.tsunagi.http.RetryPolicy;
import io.github.diegoalegil.tsunagi.model.Anime;
import io.github.diegoalegil.tsunagi.ratelimit.TokenBucketRateLimiter;
import io.github.diegoalegil.tsunagi.source.AnimeSource;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Client for <a href="https://anilist.co">AniList</a>, a GraphQL API that needs
 * no authentication. The search term always travels as a GraphQL variable, never
 * interpolated into the query, so titles with quotes or special characters are
 * safe.
 *
 * <p>Beyond the unified {@link #searchAnime} mapping, this client exposes
 * {@link #fetchPopular(int)}, which paginates the popular-anime query and returns
 * the rich {@link AniListMedia} records (titles, dates, studios, main characters,
 * tags…). An optional {@code User-Agent}, retry policy and rate limiter can be
 * supplied through the canonical constructor.
 *
 * <p>AniList answers HTTP 200 even for GraphQL-level failures (rate limiting in
 * particular), reporting them through an {@code errors} array; this client turns
 * those into a {@link RateLimitException} (or {@link TsunagiException}) instead of
 * silently treating them as "no result".
 */
public final class AniListClient implements AnimeSource {

    private static final URI DEFAULT_API_URL = URI.create("https://graphql.anilist.co");

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    // AniList caps perPage at 50: ask for more and it silently returns 50.
    // fetchPopular paginates to reach larger totals.
    private static final int MAX_PER_PAGE = 50;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;
    // The GraphQL endpoint. Configurable only through a package-private constructor
    // so tests can target a local server; public constructors use the real host.
    private final URI apiUrl;

    // All three are optional (nullable). When set, fetchPopular/fetchPage apply
    // them; searchAnime only ever uses userAgent.
    private final @Nullable String userAgent;
    private final @Nullable RetryPolicy retryPolicy;
    private final @Nullable TokenBucketRateLimiter rateLimiter;

    /** Creates a client with the default request timeout and no extras. */
    public AniListClient() {
        this(DEFAULT_REQUEST_TIMEOUT);
    }

    /** Creates a client with a custom per-request timeout and no extras. */
    public AniListClient(Duration requestTimeout) {
        this(requestTimeout, null, null, null);
    }

    /**
     * Canonical constructor.
     *
     * @param requestTimeout per-request HTTP timeout; must be positive
     * @param userAgent      optional {@code User-Agent} header value; only sent
     *                       when non-null (requires the JVM flag
     *                       {@code -Djdk.httpclient.allowRestrictedHeaders=user-agent})
     * @param retryPolicy    optional retry policy wrapping {@code fetchPage}
     * @param rateLimiter    optional rate limiter applied before each fetch
     */
    public AniListClient(Duration requestTimeout,
                         @Nullable String userAgent,
                         @Nullable RetryPolicy retryPolicy,
                         @Nullable TokenBucketRateLimiter rateLimiter) {
        this(requestTimeout, userAgent, retryPolicy, rateLimiter, DEFAULT_API_URL);
    }

    /** Package-private test seam: lets tests point the client at a local server. */
    AniListClient(Duration requestTimeout,
                  @Nullable String userAgent,
                  @Nullable RetryPolicy retryPolicy,
                  @Nullable TokenBucketRateLimiter rateLimiter,
                  URI apiUrl) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        // TMDb/AniList return many more fields than the records model, so do not
        // fail on unknown properties. This does not affect parseAnime, which uses
        // readTree/JsonNode rather than readValue.
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.requestTimeout = requireTimeout(requestTimeout);
        this.userAgent = userAgent;
        this.retryPolicy = retryPolicy;
        this.rateLimiter = rateLimiter;
        this.apiUrl = apiUrl;
    }

    private static Duration requireTimeout(Duration requestTimeout) {
        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("requestTimeout must be positive, got " + requestTimeout);
        }
        return requestTimeout;
    }

    // The search term travels as a GraphQL variable ($search), never interpolated
    // into the query string. This avoids breaking the query (or injecting into it)
    // when a title contains quotes or other special characters.
    private static final String SEARCH_QUERY = """
            query ($search: String) {
              Media(search: $search, type: ANIME) {
                id
                idMal
                title {
                  romaji
                  english
                  native
                }
                startDate {
                  year
                }
                description
                coverImage {
                  large
                }
                averageScore
                genres
                episodes
                status
              }
            }
            """;

    // Rich query used by fetchPopular: the same selection set DondeAnime relies on,
    // so the mapped records carry identical data (titles, dates, studios, up to 6
    // main characters, tags…). Sorted by popularity (then id for a stable order).
    private static final String GRAPHQL_POPULAR_QUERY = """
            query ($page: Int, $perPage: Int) {
              Page(page: $page, perPage: $perPage) {
                media(type: ANIME, sort: [POPULARITY_DESC, ID]) {
                  id
                  title { romaji english native }
                  startDate { year month day }
                  endDate { year month day }
                  episodes
                  duration
                  format
                  status
                  averageScore
                  popularity
                  description(asHtml: false)
                  coverImage { large }
                  bannerImage
                  genres
                  synonyms
                  studios { nodes { id name isAnimationStudio } }
                  season
                  seasonYear
                  characters(perPage: 6, role: MAIN) {
                    edges {
                      role
                      node {
                        id
                        name { full native }
                        image { large medium }
                      }
                    }
                  }
                  tags { name rank }
                }
              }
            }
            """;

    String buildSearchRequestBody(String title) throws JsonProcessingException {
        Map<String, Object> payload = Map.of(
                "query", SEARCH_QUERY,
                "variables", Map.of("search", title));
        return objectMapper.writeValueAsString(payload);
    }

    @Override
    public Optional<Anime> searchAnime(String title) {
        AnimeSource.requireSearchTitle(title);
        HttpResponse<String> response = send(title);
        int status = response.statusCode();

        // AniList answers 404 (not 200 with a null body) when a single Media
        // search finds nothing. Treat that as "no result", not as a failure.
        if (status == 404) {
            return Optional.empty();
        }
        if (status == 429) {
            throw new RateLimitException("AniList");
        }
        if (status != 200) {
            throw new ApiException("AniList", status);
        }

        try {
            return parseAnime(response.body());
        } catch (JsonProcessingException e) {
            throw new TsunagiException("Failed to parse AniList response", e);
        }
    }

    private HttpResponse<String> send(String title) {
        String body;
        try {
            body = buildSearchRequestBody(title);
        } catch (JsonProcessingException e) {
            throw new TsunagiException("Failed to build AniList request", e);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(apiUrl)
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        applyCommonHeaders(builder);

        try {
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new SourceUnavailableException("AniList", "request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SourceUnavailableException("AniList", "request interrupted", e);
        }
    }

    /** Adds the JSON content type and, when configured, the User-Agent header. */
    private void applyCommonHeaders(HttpRequest.Builder builder) {
        builder.header("Content-Type", "application/json");
        if (userAgent != null) {
            builder.header("User-Agent", userAgent);
        }
    }

    /**
     * Returns the {@code count} most popular anime, paginating internally in
     * blocks of {@value #MAX_PER_PAGE}. Stops early if the API runs out of
     * results, and never returns more than {@code count} entries.
     */
    public List<AniListMedia> fetchPopular(int count) {
        int perPage = Math.min(MAX_PER_PAGE, count);
        int pages = (int) Math.ceil(count / (double) perPage);

        List<AniListMedia> all = new ArrayList<>(count);
        for (int page = 1; page <= pages; page++) {
            List<AniListMedia> chunk = fetchPage(page, perPage);
            if (chunk.isEmpty()) {
                break; // AniList ran out of results
            }
            all.addAll(chunk);
            if (all.size() >= count) {
                break;
            }
        }
        return all.size() > count ? all.subList(0, count) : all;
    }

    /** Fetches a single page, applying the rate limiter and retry policy if set. */
    List<AniListMedia> fetchPage(int page, int perPage) {
        return withPolicies(() -> doFetchPage(page, perPage));
    }

    private List<AniListMedia> doFetchPage(int page, int perPage) {
        acquire();

        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "query", GRAPHQL_POPULAR_QUERY,
                    "variables", Map.of("page", page, "perPage", perPage)));
        } catch (JsonProcessingException e) {
            throw new TsunagiException("Failed to build AniList request", e);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(apiUrl)
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        applyCommonHeaders(builder);

        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new SourceUnavailableException("AniList", "request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SourceUnavailableException("AniList", "request interrupted", e);
        }

        int status = response.statusCode();
        // The Page endpoint returns 200 with an empty media array when there is
        // nothing left, so unlike searchAnime there is no 404 to special-case.
        if (status == 429) {
            throw new RateLimitException("AniList");
        }
        if (status != 200) {
            throw new ApiException("AniList", status);
        }

        try {
            return parsePopularPage(response.body());
        } catch (JsonProcessingException e) {
            throw new TsunagiException("Failed to parse AniList response", e);
        }
    }

    /** Deserializes one popular-anime page into its media list (empty if absent). */
    List<AniListMedia> parsePopularPage(String body) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(body);
        // Same 200-with-errors contract as searchAnime: surface the failure instead
        // of letting an empty page mask a rate limit and stop pagination early.
        throwOnGraphQlErrors(root);
        AniListResponse response = objectMapper.treeToValue(root, AniListResponse.class);
        if (response == null || response.data() == null || response.data().page() == null) {
            return List.of();
        }
        List<AniListMedia> media = response.data().page().media();
        return media != null ? media : List.of();
    }

    /**
     * AniList answers HTTP 200 even for GraphQL-level failures, reporting them
     * through a top-level {@code errors} array (with {@code data} set to null) —
     * rate limiting being the most common case. Without this check those bodies
     * would be read as "no result" and a transient failure would be swallowed,
     * never reaching the retry policy. Rate-limit errors become a
     * {@link RateLimitException} (retryable); anything else a {@link TsunagiException}.
     */
    private void throwOnGraphQlErrors(JsonNode root) {
        JsonNode errors = root.path("errors");
        if (!errors.isArray() || errors.isEmpty()) {
            return;
        }
        StringBuilder messages = new StringBuilder();
        for (JsonNode error : errors) {
            int status = error.path("status").asInt(0);
            String message = error.path("message").asText("");
            if (status == 429 || message.toLowerCase(Locale.ROOT).contains("too many requests")) {
                throw new RateLimitException("AniList");
            }
            if (messages.length() > 0) {
                messages.append("; ");
            }
            messages.append(message.isEmpty() ? "unknown error" : message);
            if (status > 0) {
                messages.append(" (status ").append(status).append(')');
            }
        }
        throw new TsunagiException("AniList GraphQL error: " + messages);
    }

    Optional<Anime> parseAnime(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        // AniList signals failures (e.g. rate limiting) with HTTP 200 + an errors
        // array, so check that before treating a null Media as "no result".
        throwOnGraphQlErrors(root);
        JsonNode media = root.path("data").path("Media");

        // Defensive: even on 200, Media can be null/missing if nothing matched.
        if (media.isMissingNode() || media.isNull()) {
            return Optional.empty();
        }

        // A Media without a usable id is malformed; treat it as no result.
        JsonNode idNode = media.path("id");
        if (!idNode.canConvertToInt()) {
            return Optional.empty();
        }
        String id = "anilist:" + idNode.asInt();

        // Every nested access uses path() so a missing/null object (e.g. an
        // unreleased anime with no startDate or coverImage) yields a MissingNode
        // instead of throwing a NullPointerException.
        JsonNode title = media.path("title");
        String animeTitle = firstNonNullText(
                title.path("romaji"),
                title.path("english"),
                title.path("native"));

        Integer year = intOrNull(media.path("startDate").path("year"));
        String description = textOrNull(media.path("description"));
        String imageUrl = textOrNull(media.path("coverImage").path("large"));
        Double averageScore = doubleOrNull(media.path("averageScore"));
        List<String> genres = parseStringArray(media.path("genres"));
        Integer episodes = intOrNull(media.path("episodes"));
        String status = textOrNull(media.path("status"));

        return Optional.of(new Anime(
                id,
                animeTitle,
                year,
                description,
                imageUrl,
                averageScore,
                genres,
                episodes,
                status,
                "AniList"));
    }

    private List<String> parseStringArray(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode element : node) {
            if (!element.isNull()) {
                String text = element.asText();
                if (!text.isEmpty()) {
                    values.add(text);
                }
            }
        }
        return values;
    }

    private @Nullable String textOrNull(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return text.isEmpty() ? null : text;
    }

    private @Nullable Integer intOrNull(JsonNode node) {
        return node.canConvertToInt() ? node.asInt() : null;
    }

    private @Nullable Double doubleOrNull(JsonNode node) {
        return node.isNumber() ? node.asDouble() : null;
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
            throw new SourceUnavailableException("AniList", "interrupted while rate limiting", e);
        }
    }

}
