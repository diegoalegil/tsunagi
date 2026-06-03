package io.github.diegoalegil.tsunagi.anilist;

import io.github.diegoalegil.tsunagi.exception.ApiException;
import io.github.diegoalegil.tsunagi.exception.RateLimitException;
import io.github.diegoalegil.tsunagi.exception.SourceUnavailableException;
import io.github.diegoalegil.tsunagi.exception.TsunagiException;
import io.github.diegoalegil.tsunagi.http.RetryPolicy;
import io.github.diegoalegil.tsunagi.model.Anime;
import io.github.diegoalegil.tsunagi.ratelimit.TokenBucketRateLimiter;
import io.github.diegoalegil.tsunagi.source.AnimeSource;

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
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class AniListClient implements AnimeSource {

    private static final URI API_URL = URI.create("https://graphql.anilist.co");

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    // AniList caps perPage at 50: ask for more and it silently returns 50.
    // fetchPopular paginates to reach larger totals.
    private static final int MAX_PER_PAGE = 50;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;

    // All three are optional (nullable). When set, fetchPopular/fetchPage apply
    // them; searchAnime only ever uses userAgent.
    private final String userAgent;
    private final RetryPolicy retryPolicy;
    private final TokenBucketRateLimiter rateLimiter;

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
                         String userAgent,
                         RetryPolicy retryPolicy,
                         TokenBucketRateLimiter rateLimiter) {
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
                  title { romaji english }
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
                .uri(API_URL)
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
                .uri(API_URL)
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
        AniListResponse response = objectMapper.readValue(body, AniListResponse.class);
        if (response == null || response.data() == null || response.data().page() == null) {
            return List.of();
        }
        List<AniListMedia> media = response.data().page().media();
        return media != null ? media : List.of();
    }

    Optional<Anime> parseAnime(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
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

    private String textOrNull(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return text.isEmpty() ? null : text;
    }

    private Integer intOrNull(JsonNode node) {
        return node.canConvertToInt() ? node.asInt() : null;
    }

    private Double doubleOrNull(JsonNode node) {
        return node.isNumber() ? node.asDouble() : null;
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
            throw new SourceUnavailableException("AniList", "interrupted while rate limiting", e);
        }
    }

}
