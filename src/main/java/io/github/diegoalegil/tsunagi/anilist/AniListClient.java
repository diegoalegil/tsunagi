package io.github.diegoalegil.tsunagi.anilist;

import io.github.diegoalegil.tsunagi.exception.ApiException;
import io.github.diegoalegil.tsunagi.exception.RateLimitException;
import io.github.diegoalegil.tsunagi.exception.SourceUnavailableException;
import io.github.diegoalegil.tsunagi.exception.TsunagiException;
import io.github.diegoalegil.tsunagi.model.Anime;
import io.github.diegoalegil.tsunagi.source.AnimeSource;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.net.URI;
import java.net.http.HttpClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;

public class AniListClient implements AnimeSource {

    private static final URI API_URL = URI.create("https://graphql.anilist.co");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AniListClient() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
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
              }
            }
            """;

    public String buildSearchRequestBody(String title) throws JsonProcessingException {
        Map<String, Object> payload = Map.of(
                "query", SEARCH_QUERY,
                "variables", Map.of("search", title));
        return objectMapper.writeValueAsString(payload);
    }

    @Override
    public Optional<Anime> searchAnime(String title) {
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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(API_URL)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new SourceUnavailableException("AniList", "request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SourceUnavailableException("AniList", "request interrupted", e);
        }
    }

    Optional<Anime> parseAnime(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode media = root.path("data").path("Media");

        // Defensive: even on 200, Media can be null/missing if nothing matched.
        if (media.isMissingNode() || media.isNull()) {
            return Optional.empty();
        }

        String id = "anilist:" + media.get("id").asInt();

        JsonNode title = media.get("title");
        String animeTitle = firstNonNullText(
                title.get("romaji"),
                title.get("english"),
                title.get("native"));

        Integer year = null;
        JsonNode yearNode = media.get("startDate").get("year");
        if (!yearNode.isNull()) {
            year = yearNode.asInt();
        }

        String description = null;
        JsonNode descriptionNode = media.get("description");
        if (!descriptionNode.isNull()) {
            description = descriptionNode.asText();
        }

        String imageUrl = null;
        JsonNode imageNode = media.get("coverImage").get("large");
        if (!imageNode.isNull()) {
            imageUrl = imageNode.asText();
        }

        Double averageScore = null;
        JsonNode scoreNode = media.get("averageScore");
        if (!scoreNode.isNull()) {
            averageScore = scoreNode.asDouble();
        }

        return Optional.of(new Anime(
                id,
                animeTitle,
                year,
                description,
                imageUrl,
                averageScore));
    }

    private String firstNonNullText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && !node.isNull()) {
                return node.asText();
            }
        }

        return null;
    }

}