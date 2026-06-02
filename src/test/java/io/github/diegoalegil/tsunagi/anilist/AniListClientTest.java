package io.github.diegoalegil.tsunagi.anilist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AniListClientTest {

    @Test
    void buildsSearchRequestBodyWithQueryAndVariables() throws Exception {
        AniListClient client = new AniListClient();

        String body = client.buildSearchRequestBody("Cowboy Bebop");

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode json = objectMapper.readTree(body);

        assertTrue(json.has("query"));
        assertTrue(json.get("query").asText().contains("$search"));
        assertTrue(json.get("query").asText().contains("averageScore"));

        assertTrue(json.has("variables"));
        assertEquals("Cowboy Bebop", json.get("variables").get("search").asText());
    }

    @Test
    void keepsSpecialCharactersInTitleInsteadOfBreakingTheQuery() throws Exception {
        AniListClient client = new AniListClient();

        // A title with quotes used to break the query when interpolated directly.
        String tricky = "Fate/stay night: \"Unlimited Blade Works\"";
        String body = client.buildSearchRequestBody(tricky);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode json = objectMapper.readTree(body);

        assertEquals(tricky, json.get("variables").get("search").asText());
    }
}