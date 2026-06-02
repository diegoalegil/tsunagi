package io.github.diegoalegil.tsunagi.anilist;

import io.github.diegoalegil.tsunagi.model.Anime;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class AniListClientManualTest {

    @Test
    @Disabled("Manual test: calls the real AniList API")
    void searchesAnimeInRealAniListApi() throws Exception {
        AniListClient client = new AniListClient();

        Optional<Anime> anime = client.searchAnime("Cowboy Bebop");

        System.out.println(anime);
    }
}