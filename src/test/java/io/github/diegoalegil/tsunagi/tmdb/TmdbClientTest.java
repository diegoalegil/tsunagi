package io.github.diegoalegil.tsunagi.tmdb;

import io.github.diegoalegil.tsunagi.model.Anime;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the TMDb JSON -> Anime mapping with canned responses, without
 * calling the real API.
 */
class TmdbClientTest {

    private final TmdbClient client = new TmdbClient("test-token");

    @Test
    void mapsFirstResultToAnime() throws Exception {
        String json = """
                {
                  "results": [
                    {
                      "id": 30991,
                      "name": "Cowboy Bebop",
                      "original_name": "カウボーイビバップ",
                      "overview": "Space bounty hunters.",
                      "first_air_date": "1998-04-03",
                      "poster_path": "/abc.jpg",
                      "vote_average": 8.5
                    }
                  ]
                }
                """;

        Optional<Anime> result = client.parseFirstResult(json);

        assertTrue(result.isPresent());
        Anime anime = result.get();
        assertEquals("tmdb:30991", anime.id());
        assertEquals("Cowboy Bebop", anime.title());
        assertEquals(1998, anime.year());
        assertEquals("Space bounty hunters.", anime.description());
        assertEquals("https://image.tmdb.org/t/p/w500/abc.jpg", anime.imageUrl());
        // 8.5 on TMDb's 0-10 scale becomes 85.0 on the unified 0-100 scale.
        assertEquals(85.0, anime.averageScore());
        assertEquals("TMDb", anime.source());
        // /search/tv does not include genres, episodes or status.
        assertEquals(List.of(), anime.genres());
        assertNull(anime.episodes());
        assertNull(anime.status());
    }

    @Test
    void returnsEmptyWhenResultsAreEmpty() throws Exception {
        Optional<Anime> result = client.parseFirstResult("{ \"results\": [] }");

        assertTrue(result.isEmpty());
    }

    @Test
    void keepsMissingFieldsAsNull() throws Exception {
        String json = """
                {
                  "results": [
                    {
                      "id": 7,
                      "name": "Some Show",
                      "overview": "",
                      "first_air_date": "",
                      "poster_path": null,
                      "vote_average": 0
                    }
                  ]
                }
                """;

        Optional<Anime> result = client.parseFirstResult(json);

        assertTrue(result.isPresent());
        Anime anime = result.get();
        assertEquals("Some Show", anime.title());
        assertNull(anime.year());
        assertNull(anime.description());
        assertNull(anime.imageUrl());
        assertNull(anime.averageScore());
    }

    @Test
    void rejectsNullOrBlankTitle() {
        assertThrows(IllegalArgumentException.class, () -> client.searchAnime(null));
        assertThrows(IllegalArgumentException.class, () -> client.searchAnime("   "));
    }

    @Test
    void rejectsBlankToken() {
        assertThrows(IllegalArgumentException.class, () -> new TmdbClient(null));
        assertThrows(IllegalArgumentException.class, () -> new TmdbClient(""));
        assertThrows(IllegalArgumentException.class, () -> new TmdbClient("   "));
    }
}
