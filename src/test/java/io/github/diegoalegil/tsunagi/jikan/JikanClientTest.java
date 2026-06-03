package io.github.diegoalegil.tsunagi.jikan;

import io.github.diegoalegil.tsunagi.model.Anime;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Jikan JSON -> Anime mapping with canned responses, without
 * calling the real API.
 */
class JikanClientTest {

    private final JikanClient client = new JikanClient();

    @Test
    void mapsFirstResultToAnime() throws Exception {
        String json = """
                {
                  "data": [
                    {
                      "mal_id": 1,
                      "title": "Cowboy Bebop",
                      "title_english": "Cowboy Bebop",
                      "title_japanese": "カウボーイビバップ",
                      "synopsis": "Space bounty hunters.",
                      "year": 1998,
                      "score": 8.75,
                      "images": { "jpg": {
                        "image_url": "https://img/cb.jpg",
                        "large_image_url": "https://img/cb-large.jpg"
                      } }
                    }
                  ]
                }
                """;

        Optional<Anime> result = client.parseFirstResult(json);

        assertTrue(result.isPresent());
        Anime anime = result.get();
        assertEquals("jikan:1", anime.id());
        assertEquals("Cowboy Bebop", anime.title());
        assertEquals(1998, anime.year());
        assertEquals("Space bounty hunters.", anime.description());
        assertEquals("https://img/cb-large.jpg", anime.imageUrl());
        // 8.75 on Jikan's 0-10 scale becomes 87.5 on the unified 0-100 scale.
        assertEquals(87.5, anime.averageScore());
    }

    @Test
    void rejectsNullOrBlankTitle() {
        assertThrows(IllegalArgumentException.class, () -> client.searchAnime(null));
        assertThrows(IllegalArgumentException.class, () -> client.searchAnime("  "));
    }

    @Test
    void returnsEmptyWhenDataArrayIsEmpty() throws Exception {
        Optional<Anime> result = client.parseFirstResult("{ \"data\": [] }");

        assertTrue(result.isEmpty());
    }

    @Test
    void keepsMissingFieldsAsNull() throws Exception {
        String json = """
                {
                  "data": [
                    {
                      "mal_id": 42,
                      "title": "Some Anime",
                      "synopsis": null,
                      "year": null,
                      "score": null,
                      "images": { "jpg": {} }
                    }
                  ]
                }
                """;

        Optional<Anime> result = client.parseFirstResult(json);

        assertTrue(result.isPresent());
        Anime anime = result.get();
        assertEquals("Some Anime", anime.title());
        assertNull(anime.year());
        assertNull(anime.description());
        assertNull(anime.imageUrl());
        assertNull(anime.averageScore());
    }

    @Test
    void fallsBackToAiredYearWhenTopLevelYearIsMissing() throws Exception {
        String json = """
                {
                  "data": [
                    {
                      "mal_id": 7,
                      "title": "Old Show",
                      "year": null,
                      "aired": { "prop": { "from": { "year": 1985 } } }
                    }
                  ]
                }
                """;

        Optional<Anime> result = client.parseFirstResult(json);

        assertTrue(result.isPresent());
        assertEquals(1985, result.get().year());
    }
}
