package io.github.diegoalegil.tsunagi.anilist;

import io.github.diegoalegil.tsunagi.model.Anime;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the JSON -> Anime mapping with canned AniList responses,
 * without calling the real API.
 */
class AniListMappingTest {

    private final AniListClient client = new AniListClient();

    @Test
    void mapsCompleteResponseToAnime() throws Exception {
        String json = """
                {
                  "data": {
                    "Media": {
                      "id": 1,
                      "idMal": 1,
                      "title": { "romaji": "Cowboy Bebop", "english": "Cowboy Bebop", "native": "カウボーイビバップ" },
                      "startDate": { "year": 1998 },
                      "description": "Space bounty hunters.",
                      "coverImage": { "large": "https://img/cb.jpg" },
                      "averageScore": 86,
                      "genres": ["Action", "Sci-Fi"],
                      "episodes": 26,
                      "status": "FINISHED"
                    }
                  }
                }
                """;

        Optional<Anime> result = client.parseAnime(json);

        assertTrue(result.isPresent());
        Anime anime = result.get();
        assertEquals("anilist:1", anime.id());
        assertEquals("Cowboy Bebop", anime.title());
        assertEquals(1998, anime.year());
        assertEquals("Space bounty hunters.", anime.description());
        assertEquals("https://img/cb.jpg", anime.imageUrl());
        assertEquals(86.0, anime.averageScore());
        assertEquals(List.of("Action", "Sci-Fi"), anime.genres());
        assertEquals(26, anime.episodes());
        assertEquals("FINISHED", anime.status());
        assertEquals("AniList", anime.source());
    }

    @Test
    void rejectsNullOrBlankTitle() {
        assertThrows(IllegalArgumentException.class, () -> client.searchAnime(null));
        assertThrows(IllegalArgumentException.class, () -> client.searchAnime(" "));
    }

    @Test
    void rejectsInvalidRequestTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new AniListClient(null));
        assertThrows(IllegalArgumentException.class, () -> new AniListClient(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new AniListClient(Duration.ofSeconds(-1)));
    }

    @Test
    void returnsEmptyWhenMediaIsNull() throws Exception {
        String json = """
                { "data": { "Media": null } }
                """;

        Optional<Anime> result = client.parseAnime(json);

        assertTrue(result.isEmpty());
    }

    @Test
    void keepsNullFieldsAsNullInsteadOfFakeDefaults() throws Exception {
        String json = """
                {
                  "data": {
                    "Media": {
                      "id": 42,
                      "title": { "romaji": null, "english": null, "native": "ナルト" },
                      "startDate": { "year": null },
                      "description": null,
                      "coverImage": { "large": null },
                      "averageScore": null
                    }
                  }
                }
                """;

        Optional<Anime> result = client.parseAnime(json);

        assertTrue(result.isPresent());
        Anime anime = result.get();
        assertEquals("ナルト", anime.title()); // falls back to native
        assertNull(anime.year());
        assertNull(anime.description());
        assertNull(anime.imageUrl());
        assertNull(anime.averageScore());
        assertTrue(anime.genres().isEmpty());
        assertNull(anime.episodes());
        assertNull(anime.status());
        assertEquals("AniList", anime.source());
    }

    @Test
    void handlesMissingNestedObjectsWithoutNullPointer() throws Exception {
        // No title, startDate, coverImage or averageScore objects at all.
        String json = """
                {
                  "data": {
                    "Media": { "id": 99 }
                  }
                }
                """;

        Optional<Anime> result = client.parseAnime(json);

        assertTrue(result.isPresent());
        Anime anime = result.get();
        assertEquals("anilist:99", anime.id());
        assertNull(anime.title());
        assertNull(anime.year());
        assertNull(anime.description());
        assertNull(anime.imageUrl());
        assertNull(anime.averageScore());
    }

    @Test
    void returnsEmptyWhenMediaHasNoUsableId() throws Exception {
        Optional<Anime> result = client.parseAnime("{ \"data\": { \"Media\": { \"title\": { \"romaji\": \"X\" } } } }");

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsEmptyWhenDataHasNoMediaField() throws Exception {
        String json = """
                { "data": {} }
                """;

        Optional<Anime> result = client.parseAnime(json);

        assertFalse(result.isPresent());
    }
}
