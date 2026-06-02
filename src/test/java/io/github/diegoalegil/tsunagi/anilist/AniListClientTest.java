package io.github.diegoalegil.tsunagi.anilist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AniListClientTest {

    @Test
    void buildsSearchRequestBodyWithQuery() throws Exception {
        AniListClient client = new AniListClient();

        String body = client.buildSearchRequestBody("Cowboy Bebop");

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode json = objectMapper.readTree(body);

        assertTrue(json.has("query"));
        assertTrue(json.get("query").asText().contains("Cowboy Bebop"));
        assertTrue(json.get("query").asText().contains("averageScore"));
    }
}