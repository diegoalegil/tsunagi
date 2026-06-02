package io.github.diegoalegil.tsunagi.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimeTest {

    @Test
    void createsAnimeWithBasicFields() {
        Anime anime = new Anime(
                "anilist:1",
                "Cowboy Bebop",
                1998,
                "Space bounty hunters.",
                "https://example.com/cowboy-bebop.jpg",
                86.0
        );

        assertEquals("Cowboy Bebop", anime.title());
        assertEquals(1998, anime.year());
        assertEquals(86.0, anime.averageScore());
    }
}