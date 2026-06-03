package io.github.diegoalegil.tsunagi.jikan;

import io.github.diegoalegil.tsunagi.model.Anime;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class JikanClientManualTest {

    @Test
    @Disabled("Manual test: calls the real Jikan API")
    void searchesAnimeInRealJikanApi() throws Exception {
        JikanClient client = new JikanClient();

        Optional<Anime> anime = client.searchAnime("Cowboy Bebop");

        System.out.println(anime);
    }
}
