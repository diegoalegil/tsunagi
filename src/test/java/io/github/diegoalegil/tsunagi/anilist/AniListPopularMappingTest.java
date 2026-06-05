package io.github.diegoalegil.tsunagi.anilist;

import io.github.diegoalegil.tsunagi.exception.RateLimitException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the popular-anime page mapping (readValue into records) with canned
 * AniList responses, without calling the real API.
 */
class AniListPopularMappingTest {

    private final AniListClient client = new AniListClient();

    @Test
    void mapsCompleteMediaWithRichFields() throws Exception {
        String json = """
                {
                  "data": {
                    "Page": {
                      "media": [
                        {
                          "id": 1,
                          "title": { "romaji": "Cowboy Bebop", "english": "Cowboy Bebop", "native": "カウボーイビバップ" },
                          "synonyms": ["Kaubōi Bibappu", "COWBOY BEBOP"],
                          "startDate": { "year": 1998, "month": 4, "day": 3 },
                          "endDate": { "year": 1999, "month": 4, "day": 24 },
                          "episodes": 26,
                          "duration": 24,
                          "format": "TV",
                          "status": "FINISHED",
                          "averageScore": 86,
                          "popularity": 500000,
                          "description": "Space bounty hunters.",
                          "coverImage": { "large": "https://img/cb.jpg" },
                          "bannerImage": "https://img/cb-banner.jpg",
                          "genres": ["Action", "Sci-Fi"],
                          "studios": { "nodes": [
                            { "id": 14, "name": "Sunrise", "isAnimationStudio": true },
                            { "id": 23, "name": "Bandai", "isAnimationStudio": false }
                          ] },
                          "season": "SPRING",
                          "seasonYear": 1998,
                          "characters": { "edges": [
                            { "role": "MAIN", "node": { "id": 1, "name": { "full": "Spike Spiegel", "native": "スパイク・スピーゲル" }, "image": { "large": "l1.jpg", "medium": "m1.jpg" } } },
                            { "role": "MAIN", "node": { "id": 2, "name": { "full": "Jet Black", "native": "ジェット・ブラック" }, "image": { "large": "l2.jpg", "medium": "m2.jpg" } } },
                            { "role": "MAIN", "node": { "id": 3, "name": { "full": "Faye Valentine", "native": "フェイ・ヴァレンタイン" }, "image": { "large": "l3.jpg", "medium": "m3.jpg" } } },
                            { "role": "MAIN", "node": { "id": 4, "name": { "full": "Edward", "native": "エド" }, "image": { "large": "l4.jpg", "medium": "m4.jpg" } } },
                            { "role": "MAIN", "node": { "id": 5, "name": { "full": "Ein", "native": "アイン" }, "image": { "large": "l5.jpg", "medium": "m5.jpg" } } },
                            { "role": "SUPPORTING", "node": { "id": 6, "name": { "full": "Vicious", "native": "ビシャス" }, "image": { "large": "l6.jpg", "medium": "m6.jpg" } } }
                          ] },
                          "tags": [
                            { "name": "Space", "rank": 95 },
                            { "name": "Bounty Hunter", "rank": 88 }
                          ]
                        }
                      ]
                    }
                  }
                }
                """;

        List<AniListMedia> media = client.parsePopularPage(json);

        assertEquals(1, media.size());
        AniListMedia m = media.get(0);
        assertEquals(1L, m.id());
        assertEquals("Cowboy Bebop", m.title().romaji());
        assertEquals("Cowboy Bebop", m.title().english());
        assertEquals("カウボーイビバップ", m.title().nativeTitle());
        assertEquals(List.of("Kaubōi Bibappu", "COWBOY BEBOP"), m.synonyms());

        // Fuzzy dates: year/month/day all present on both ends.
        assertEquals(1998, m.startDate().year());
        assertEquals(4, m.startDate().month());
        assertEquals(3, m.startDate().day());
        assertEquals(1999, m.endDate().year());
        assertEquals(24, m.endDate().day());

        assertEquals(26, m.episodes());
        assertEquals(24, m.duration());
        assertEquals("TV", m.format());
        assertEquals("FINISHED", m.status());
        assertEquals(86, m.averageScore());
        assertEquals(500000, m.popularity());
        assertEquals("Space bounty hunters.", m.description());
        assertEquals("https://img/cb.jpg", m.coverImage().large());
        assertEquals("https://img/cb-banner.jpg", m.bannerImage());
        assertEquals(List.of("Action", "Sci-Fi"), m.genres());
        assertEquals("SPRING", m.season());
        assertEquals(1998, m.seasonYear());

        // Studios: isAnimationStudio distinguishes the animation studio.
        assertEquals(2, m.studios().nodes().size());
        assertEquals("Sunrise", m.studios().nodes().get(0).name());
        assertTrue(m.studios().nodes().get(0).isAnimationStudio());
        assertEquals(Boolean.FALSE, m.studios().nodes().get(1).isAnimationStudio());

        // Up to 6 main characters, each with role, name (full + native) and image.
        assertEquals(6, m.characters().edges().size());
        AniListCharacterEdge first = m.characters().edges().get(0);
        assertEquals("MAIN", first.role());
        assertEquals("Spike Spiegel", first.node().name().full());
        assertEquals("スパイク・スピーゲル", first.node().name().nativeName());
        assertEquals("l1.jpg", first.node().image().large());
        assertEquals("m1.jpg", first.node().image().medium());
        assertEquals("SUPPORTING", m.characters().edges().get(5).role());

        // Tags carry a relevance rank.
        assertEquals(2, m.tags().size());
        assertEquals("Space", m.tags().get(0).name());
        assertEquals(95, m.tags().get(0).rank());
    }

    @Test
    void keepsNestedNullsAsNullForSparseMedia() throws Exception {
        // No native title, synonyms, startDate, studios, characters or tags.
        String json = """
                {
                  "data": {
                    "Page": {
                      "media": [
                        {
                          "id": 42,
                          "title": { "romaji": "Naruto", "english": null },
                          "episodes": null,
                          "coverImage": { "large": null }
                        }
                      ]
                    }
                  }
                }
                """;

        List<AniListMedia> media = client.parsePopularPage(json);

        assertEquals(1, media.size());
        AniListMedia m = media.get(0);
        assertEquals(42L, m.id());
        assertEquals("Naruto", m.title().romaji());
        assertNull(m.title().english());
        assertNull(m.title().nativeTitle());
        assertNull(m.synonyms());
        assertNull(m.startDate());
        assertNull(m.endDate());
        assertNull(m.episodes());
        assertNull(m.studios());
        assertNull(m.characters());
        assertNull(m.tags());
        assertNull(m.coverImage().large());
    }

    @Test
    void deserializesMediaWithoutIdAsNullId() throws Exception {
        // Unlike the single-Media searchAnime path, the popular page does not
        // filter: a media without an id simply maps to a null id.
        String json = """
                {
                  "data": {
                    "Page": {
                      "media": [
                        { "title": { "romaji": "No Id Show", "english": null } }
                      ]
                    }
                  }
                }
                """;

        List<AniListMedia> media = client.parsePopularPage(json);

        assertEquals(1, media.size());
        assertNull(media.get(0).id());
        assertEquals("No Id Show", media.get(0).title().romaji());
    }

    @Test
    void returnsEmptyListWhenPageIsNull() throws Exception {
        assertTrue(client.parsePopularPage("{ \"data\": { \"Page\": null } }").isEmpty());
    }

    @Test
    void returnsEmptyListWhenDataIsMissing() throws Exception {
        assertTrue(client.parsePopularPage("{}").isEmpty());
    }

    @Test
    void throwsRateLimitInsteadOfStoppingPaginationOnGraphQlError() {
        // A rate-limited page must raise (and let the retry policy react) rather
        // than return empty, which fetchPopular would read as "ran out of results".
        String json = """
                {
                  "data": null,
                  "errors": [ { "message": "Too Many Requests.", "status": 429 } ]
                }
                """;

        assertThrows(RateLimitException.class, () -> client.parsePopularPage(json));
    }
}
