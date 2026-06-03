package io.github.diegoalegil.tsunagi.tmdb;

import io.github.diegoalegil.tsunagi.model.Anime;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class TmdbClientManualTest {

    @Test
    @Disabled("Manual test: calls the real TMDb API; needs TMDB_TOKEN in the environment")
    void searchesShowInRealTmdbApi() throws Exception {
        String token = System.getenv("TMDB_TOKEN");
        TmdbClient client = new TmdbClient(token);

        Optional<Anime> anime = client.searchAnime("Cowboy Bebop");

        System.out.println(anime);
    }
}
