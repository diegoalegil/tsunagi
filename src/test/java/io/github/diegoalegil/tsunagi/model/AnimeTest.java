package io.github.diegoalegil.tsunagi.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimeTest {

    @Test
    void createsAnimeWithAllFields() {
        Anime anime = new Anime(
                "anilist:1",
                "Cowboy Bebop",
                1998,
                "Space bounty hunters.",
                "https://example.com/cowboy-bebop.jpg",
                86.0,
                List.of("Action", "Sci-Fi"),
                26,
                "FINISHED",
                "AniList"
        );

        assertEquals("Cowboy Bebop", anime.title());
        assertEquals(1998, anime.year());
        assertEquals(86.0, anime.averageScore());
        assertEquals(List.of("Action", "Sci-Fi"), anime.genres());
        assertEquals(26, anime.episodes());
        assertEquals("FINISHED", anime.status());
        assertEquals("AniList", anime.source());
    }

    @Test
    void genresDefaultsToEmptyWhenNull() {
        Anime anime = new Anime("x", "X", null, null, null, null, null, null, null, "AniList");

        assertTrue(anime.genres().isEmpty());
    }

    @Test
    void genresAreImmutable() {
        List<String> mutable = new ArrayList<>(List.of("Action"));
        Anime anime = new Anime("x", "X", null, null, null, null, mutable, null, null, "AniList");

        // The stored list is an immutable copy: mutating the input does not leak in,
        // and the accessor's list cannot be modified.
        mutable.add("Drama");
        assertEquals(List.of("Action"), anime.genres());
        assertThrows(UnsupportedOperationException.class, () -> anime.genres().add("Comedy"));
    }
}
