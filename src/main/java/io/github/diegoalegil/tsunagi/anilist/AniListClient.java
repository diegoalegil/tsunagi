package io.github.diegoalegil.tsunagi.anilist;

import io.github.diegoalegil.tsunagi.model.Anime;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.net.URI;
import java.net.http.HttpClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class AniListClient {

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

    public Anime searchAnime(String title) throws IOException, InterruptedException {
        String body = buildSearchRequestBody(title);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(API_URL)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("AniList request failed with status " + response.statusCode());
        }

        return parseAnime(response.body());
    }

    private Anime parseAnime(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode media = root.get("data").get("Media");

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

        return new Anime(
                id,
                animeTitle,
                year,
                description,
                imageUrl,
                averageScore);
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